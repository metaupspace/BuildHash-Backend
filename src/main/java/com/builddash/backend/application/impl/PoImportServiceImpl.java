package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.PoImportStatus;
import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.PoImportRepository;
import com.builddash.backend.domain.port.PoImportRowRepository;
import com.builddash.backend.domain.port.PoWorkbookParser;
import com.builddash.backend.domain.port.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bulk import pipeline:
 *
 *   Tx1   authorize(PO_UPLOAD) + idempotency pre-read — replay returns the
 *         existing resource without reparse (FAILED_STRUCTURE included)
 *   CPU   stream parse + row validation + duplicate-slug aggregation
 *         (no database connection held, no MultipartFile.getBytes())
 *   Tx2   persist import + every source row; >=1 valid row also creates the
 *         B2B_DRAFT cart in the same transaction. UNIQUE(company_id, key) is
 *         the final backstop — the race loser re-reads the winner's resource.
 *
 * Structural failure persists a FAILED_STRUCTURE import (key consumed) and
 * rethrows the 400 for the request that did the parse.
 */
@Service
public class PoImportServiceImpl implements PoImportService {

    /** products.slug column cap; longer source values fail the row (SKU_INVALID). */
    private static final int MAX_SLUG_LENGTH = 255;
    static final int MAX_QUANTITY = 10000;

    private final B2bAuthorizer b2bAuthorizer;
    private final CartService cartService;
    private final PoImportRepository poImportRepository;
    private final PoImportRowRepository poImportRowRepository;
    private final PoWorkbookParser parser;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public PoImportServiceImpl(B2bAuthorizer b2bAuthorizer, CartService cartService,
                               PoImportRepository poImportRepository,
                               PoImportRowRepository poImportRowRepository,
                               PoWorkbookParser parser, ProductRepository productRepository,
                               TransactionTemplate transactionTemplate) {
        this.b2bAuthorizer = b2bAuthorizer;
        this.cartService = cartService;
        this.poImportRepository = poImportRepository;
        this.poImportRowRepository = poImportRowRepository;
        this.parser = parser;
        this.productRepository = productRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ImportResult importWorkbook(UUID userId, UUID companyId, String idempotencyKey,
                                       MultipartFile file) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PoImportValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required");
        }

        ImportResult replay = transactionTemplate.execute(tx -> {
            b2bAuthorizer.authorize(userId, companyId, CompanyPermission.PO_UPLOAD, null, true);
            Optional<PoImport> existing = poImportRepository
                    .findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey);
            return existing
                    .map(found -> (ImportResult) new ImportResult(found,
                            poImportRowRepository.findByImportId(found.id()), true))
                    .orElse(null);
        });
        if (replay != null) {
            return replay; // same key — existing resource, no reparse, no storage read
        }

        List<PoWorkbookParser.PoRawRow> rawRows;
        try {
            rawRows = parse(file); // outside any transaction
        } catch (PoImportValidationException structural) {
            persistStructuralFailure(userId, companyId, idempotencyKey);
            throw structural; // 400 for the request that performed the failed parse
        }

        ValidatedRows validated = validateRows(rawRows);

        try {
            return transactionTemplate.execute(tx -> {
                PoImport saved = persist(userId, companyId, idempotencyKey, validated);
                return new ImportResult(saved,
                        poImportRowRepository.findByImportId(saved.id()), false);
            });
        } catch (DataIntegrityViolationException e) {
            // Lost the UNIQUE(company_id, idempotency_key) race — the winner stands.
            // The catch sits OUTSIDE the transaction: Postgres aborts the whole tx
            // on the constraint violation, so the re-read must run in a fresh one.
            PoImport winner = poImportRepository
                    .findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey)
                    .orElseThrow(() -> new PoImportValidationException("IDEMPOTENCY_CONFLICT",
                            "Concurrent import with the same key could not be resolved"));
            return new ImportResult(winner,
                    poImportRowRepository.findByImportId(winner.id()), true);
        }
    }

    @Override
    public ImportDetail get(UUID userId, UUID importId) {
        PoImport poImport = poImportRepository.findById(importId)
                .orElseThrow(() -> new NotFoundException("PO_IMPORT_NOT_FOUND",
                        "PO import not found: " + importId));
        b2bAuthorizer.authorize(userId, poImport.companyId(), CompanyPermission.PO_VIEW, null, false);
        return new ImportDetail(poImport, poImportRowRepository.findByImportId(importId));
    }

    private List<PoWorkbookParser.PoRawRow> parse(MultipartFile file) {
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
        if (file.getSize() > PoAttachmentServiceImpl.MAX_BYTES) {
            throw new PoImportValidationException("FILE_TOO_LARGE", "File exceeds the 2MB limit");
        }
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            throw new PoImportValidationException("INVALID_WORKBOOK", "File could not be read");
        }
    }

    /**
     * Per-row validation first (locked decision 8), then aggregation: valid rows
     * sharing a productSlug merge; a merged quantity above the cap invalidates
     * every contributing row — never silently clamped.
     */
    private ValidatedRows validateRows(List<PoWorkbookParser.PoRawRow> rawRows) {
        UUID importId = UUID.randomUUID(); // rows reference the import being created
        Map<String, UUID> resolvedProducts = new LinkedHashMap<>();
        List<RowDraft> drafts = new ArrayList<>();

        for (PoWorkbookParser.PoRawRow raw : rawRows) {
            String errorCode = raw.errorCode();
            String slug = raw.productSlug();
            Long qty = raw.quantity();

            if (errorCode == null) {
                if (slug == null || slug.isBlank()) {
                    errorCode = "SKU_REQUIRED";
                } else if (slug.length() > MAX_SLUG_LENGTH) {
                    errorCode = "SKU_INVALID";
                }
            }
            if (errorCode == null && (qty == null || qty < 1 || qty > MAX_QUANTITY)) {
                errorCode = "QTY_INVALID";
            }
            if (errorCode == null) {
                List<Product> matches = resolvedProducts.containsKey(slug)
                        ? List.of() // already resolved below
                        : productRepository.findBySlug(slug);
                if (!resolvedProducts.containsKey(slug)) {
                    if (matches.isEmpty()) {
                        errorCode = "PRODUCT_SLUG_NOT_FOUND";
                    } else if (matches.size() > 1) {
                        // slug is not schema-unique: ambiguity is an error, never first-match
                        errorCode = "PRODUCT_SLUG_AMBIGUOUS";
                    } else {
                        resolvedProducts.put(slug, matches.get(0).getId());
                    }
                }
            }
            drafts.add(new RowDraft(raw.rowIndex(), slug, qty, errorCode));
        }

        // Aggregation of VALID rows by resolved product
        Map<String, List<RowDraft>> bySlug = new LinkedHashMap<>();
        for (RowDraft draft : drafts) {
            if (draft.errorCode == null) {
                bySlug.computeIfAbsent(draft.slug, k -> new ArrayList<>()).add(draft);
            }
        }
        for (List<RowDraft> group : bySlug.values()) {
            long sum = group.stream().mapToLong(d -> d.qty).sum();
            if (sum > MAX_QUANTITY) {
                group.forEach(d -> d.errorCode = "QTY_INVALID"); // merged quantity over cap
            }
        }

        List<PoImportRow> rows = new ArrayList<>(drafts.size());
        Map<String, Integer> merged = new LinkedHashMap<>();
        int valid = 0;
        for (RowDraft draft : drafts) {
            boolean isValid = draft.errorCode == null;
            if (isValid) {
                valid++;
                merged.merge(draft.slug, draft.qty.intValue(), Integer::sum);
            }
            rows.add(new PoImportRow(UUID.randomUUID(), importId, draft.rowIndex,
                    draft.slug, draft.qty == null ? null : draft.qty.intValue(),
                    isValid ? PoRowStatus.VALID : PoRowStatus.INVALID, draft.errorCode));
        }
        List<CartLineItem> cartItems = merged.entrySet().stream()
                .map(e -> new CartLineItem(null, null, resolvedProducts.get(e.getKey()),
                        e.getValue(), null))
                .toList();
        return new ValidatedRows(importId, rows, valid, rows.size() - valid, cartItems);
    }

    private PoImport persist(UUID userId, UUID companyId, String idempotencyKey,
                             ValidatedRows validated) {
        UUID draftCartId = null;
        if (validated.validRows() > 0) {
            draftCartId = cartService
                    .createB2bDraftCart(companyId, userId, validated.importId(), validated.cartItems())
                    .id();
        }
        PoImport saved = poImportRepository.save(new PoImport(
                validated.importId(), companyId, idempotencyKey, userId,
                PoImportStatus.REVIEW, validated.rows().size(), validated.validRows(),
                validated.rows().size() - validated.validRows(), draftCartId, null, null));
        poImportRowRepository.saveAll(validated.rows());
        return saved;
    }

    /** Structural failure still consumes the key (locked decision 6). */
    private void persistStructuralFailure(UUID userId, UUID companyId, String idempotencyKey) {
        transactionTemplate.executeWithoutResult(tx -> {
            try {
                poImportRepository.save(new PoImport(UUID.randomUUID(), companyId, idempotencyKey,
                        userId, PoImportStatus.FAILED_STRUCTURE, 0, 0, 0, null, null, null));
            } catch (DataIntegrityViolationException e) {
                // Key already consumed (replay raced): the existing record stands.
            }
        });
    }

    private static final class RowDraft {
        private final int rowIndex;
        private final String slug;
        private final Long qty;
        private String errorCode;

        private RowDraft(int rowIndex, String slug, Long qty, String errorCode) {
            this.rowIndex = rowIndex;
            this.slug = slug;
            this.qty = qty;
            this.errorCode = errorCode;
        }
    }

    private record ValidatedRows(UUID importId, List<PoImportRow> rows, int validRows,
                                 int invalidRows, List<CartLineItem> cartItems) {
    }
}
