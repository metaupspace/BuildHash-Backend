package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.PoAttachmentService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.PoAttachmentStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PoAttachmentExistsException;
import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.exception.PoUploadInProgressException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.PoAttachment;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PoAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable-claim attachment flow (refund-gateway discipline, 48aeabe):
 *
 *   Tx1   lock order -> B2C rejection -> authorize(PO_UPLOAD, order.siteId, critical)
 *         -> claim check (STORED -> 409, PENDING -> 409 IN_PROGRESS on fresh upload)
 *         -> persist PENDING with id + storageKey generated here
 *   Ext   ObjectStorage.store — NO transaction held
 *   Tx2   conditional PENDING->STORED finalize; 0 affected rows means a
 *         concurrent retry won — the loser re-reads and returns the winner's state.
 *
 * A failed store leaves the claim PENDING and recoverable via
 * {@link #retry}, which is the only operation that may reuse it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoAttachmentServiceImpl implements PoAttachmentService {

    /** Locked resource limits: 2MB, XLSX content only. */
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final byte[] ZIP_LOCAL_FILE_HEADER = {0x50, 0x4B, 0x03, 0x04};

    private final B2bAuthorizer b2bAuthorizer;
    private final OrderRepository orderRepository;
    private final PoAttachmentRepository poAttachmentRepository;
    private final ObjectStorage objectStorage;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PoAttachment upload(UUID userId, UUID orderId, MultipartFile file) {
        byte[] bytes = validate(file);

        PoAttachment claim;
        try {
            claim = transactionTemplate.execute(tx -> {
                Order order = lockAndAuthorize(userId, orderId);
                poAttachmentRepository.findByOrderId(orderId).ifPresent(existing -> {
                    if (existing.status() == PoAttachmentStatus.STORED) {
                        throw new PoAttachmentExistsException(orderId, existing.id());
                    }
                    throw new PoUploadInProgressException(orderId, existing.id());
                });
                UUID attachmentId = UUID.randomUUID();
                PoAttachment pending = new PoAttachment(attachmentId, orderId,
                        storageKey(orderId, attachmentId), XLSX_MIME, bytes.length, userId,
                        PoAttachmentStatus.PENDING, null, null);
                return poAttachmentRepository.save(pending);
            });
        } catch (DataIntegrityViolationException e) {
            // Lost the UNIQUE(order_id) race: the other upload's claim stands. Catch
            // sits OUTSIDE the transaction — Postgres aborts the tx on the violation,
            // so the re-read must run in a fresh one.
            PoAttachment winner = poAttachmentRepository.findByOrderId(orderId).orElseThrow();
            if (winner.status() == PoAttachmentStatus.STORED) {
                throw new PoAttachmentExistsException(orderId, winner.id());
            }
            throw new PoUploadInProgressException(orderId, winner.id());
        }

        objectStorage.store(claim.storageKey(), bytes, XLSX_MIME);

        return poAttachmentRepository.finalizeStored(claim.id(), XLSX_MIME, bytes.length, userId)
                .orElseGet(() -> poAttachmentRepository.findById(claim.id()).orElseThrow());
    }

    @Override
    public RetryOutcome retry(UUID userId, UUID orderId, UUID attachmentId, MultipartFile file) {
        byte[] bytes = validate(file);

        transactionTemplate.executeWithoutResult(tx -> {
            lockAndAuthorize(userId, orderId);
            PoAttachment claim = poAttachmentRepository.findByIdForUpdate(attachmentId)
                    .filter(a -> a.orderId().equals(orderId))
                    .orElseThrow(() -> new NotFoundException("ATTACHMENT_NOT_FOUND",
                            "PO attachment not found for order " + orderId + ": " + attachmentId));
            if (claim.status() == PoAttachmentStatus.STORED) {
                throw new PoAttachmentExistsException(orderId, claim.id());
            }
        });

        PoAttachment claim = poAttachmentRepository.findById(attachmentId).orElseThrow();
        // Same id, same storage key: the put is an idempotent overwrite of this
        // claim's own object whether or not the earlier store attempt landed.
        objectStorage.store(claim.storageKey(), bytes, XLSX_MIME);

        Optional<PoAttachment> finalized =
                poAttachmentRepository.finalizeStored(claim.id(), XLSX_MIME, bytes.length, userId);
        return finalized.map(a -> new RetryOutcome(a, true))
                .orElseGet(() -> new RetryOutcome(
                        poAttachmentRepository.findById(claim.id()).orElseThrow(), false));
    }

    /**
     * Tx1 order gate. B2C orders are rejected with ORDER_NOT_FOUND — the B2B
     * endpoint must not reveal or attach to consumer orders.
     */
    private Order lockAndAuthorize(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId));
        if (order.companyId() == null) {
            throw new NotFoundException("ORDER_NOT_FOUND",
                    "Order not found: " + orderId + " (not a B2B order)");
        }
        b2bAuthorizer.authorize(userId, order.companyId(), CompanyPermission.PO_UPLOAD,
                order.siteId(), true);
        return order;
    }

    /** Cheap payload validation before any database work. Throws 400-level codes. */
    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PoImportValidationException("EMPTY_FILE", "Uploaded file is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new PoImportValidationException("INVALID_FILE_NAME",
                    "File name must end with .xlsx");
        }
        if (filename.contains("/") || filename.contains("\\") || filename.contains("\0")) {
            throw new PoImportValidationException("INVALID_FILE_NAME",
                    "File name must not contain path separators");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new PoImportValidationException("FILE_TOO_LARGE",
                    "File exceeds the 2MB limit");
        }
        String declared = file.getContentType();
        if (declared != null && !XLSX_MIME.equalsIgnoreCase(declared)
                && !"application/octet-stream".equalsIgnoreCase(declared)
                && !"application/zip".equalsIgnoreCase(declared)) {
            throw new PoImportValidationException("INVALID_CONTENT_TYPE",
                    "Content type must be " + XLSX_MIME);
        }
        byte[] bytes = read(file);
        if (bytes.length > MAX_BYTES) {
            throw new PoImportValidationException("FILE_TOO_LARGE", "File exceeds the 2MB limit");
        }
        if (bytes.length < 4 || !Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), ZIP_LOCAL_FILE_HEADER)) {
            // Declared content type is a hint; the OOXML/ZIP signature decides.
            throw new PoImportValidationException("INVALID_CONTENT_TYPE",
                    "File is not an XLSX workbook (missing OOXML/ZIP signature)");
        }
        return bytes;
    }

    private byte[] read(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes(); // bounded by the 2MB checks above
        } catch (IOException e) {
            throw new PoImportValidationException("INVALID_WORKBOOK", "File could not be read");
        }
    }

    private String storageKey(UUID orderId, UUID attachmentId) {
        return "po/" + orderId + "/" + attachmentId + ".xlsx";
    }
}
