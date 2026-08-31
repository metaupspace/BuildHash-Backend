package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.PoAttachmentService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoAttachmentServiceImplTest {

    private static final byte[] VALID_XLSX_BYTES = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4};

    private B2bAuthorizer b2bAuthorizer;
    private OrderRepository orderRepository;
    private PoAttachmentRepository poAttachmentRepository;
    private ObjectStorage objectStorage;
    private TransactionTemplate transactionTemplate;
    private PoAttachmentServiceImpl service;

    private UUID userId;
    private UUID orderId;
    private Order b2bOrder;

    @BeforeEach
    void setUp() {
        b2bAuthorizer = mock(B2bAuthorizer.class);
        orderRepository = mock(OrderRepository.class);
        poAttachmentRepository = mock(PoAttachmentRepository.class);
        objectStorage = mock(ObjectStorage.class);
        transactionTemplate = mock(TransactionTemplate.class);
        // run callbacks inline: no real transactions in unit tests
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        doAnswer(inv -> {
            ((Consumer<?>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new PoAttachmentServiceImpl(b2bAuthorizer, orderRepository,
                poAttachmentRepository, objectStorage, transactionTemplate);

        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        b2bOrder = b2bOrder(orderId, null);
        lenient().when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(b2bOrder));
        lenient().when(poAttachmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        lenient().when(poAttachmentRepository.save(any(PoAttachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Order b2bOrder(UUID id, UUID siteId) {
        return new Order(id, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                java.math.BigDecimal.TEN, null, null, null, null, null, null,
                UUID.randomUUID(), siteId, null);
    }

    private MultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    @Test
    void upload_orderMissing_throws404() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void upload_b2cOrder_hiddenAs404() {
        Order b2c = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                java.math.BigDecimal.TEN, null, null, null, null, null, null, null, null, null);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(b2c));

        assertThatThrownBy(() -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ORDER_NOT_FOUND");
        verify(objectStorage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void upload_storedExists_409_noOverwrite() {
        when(poAttachmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(
                attachment(PoAttachmentStatus.STORED)));

        assertThatThrownBy(() -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)))
                .isInstanceOf(PoAttachmentExistsException.class)
                .extracting("code").isEqualTo("PO_ALREADY_EXISTS");
        verify(objectStorage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void upload_pendingExists_409_inProgress_namesClaim_noStore() {
        PoAttachment pending = attachment(PoAttachmentStatus.PENDING);
        when(poAttachmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pending));

        PoUploadInProgressException inProgress = catchThrowableOfType(
                () -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)),
                PoUploadInProgressException.class);
        assertThat(inProgress.getCode()).isEqualTo("PO_UPLOAD_IN_PROGRESS");
        assertThat(inProgress.getMessage()).contains(pending.id().toString());
        // THE locked-decision guarantee: a fresh upload never touches the pending object
        verify(objectStorage, never()).store(anyString(), any(), anyString());
        verify(poAttachmentRepository, never()).save(any(PoAttachment.class));
    }

    @Test
    void upload_happyPath_claimsStoresFinalizes() {
        PoAttachment stored = attachment(PoAttachmentStatus.PENDING).stored();
        when(poAttachmentRepository.finalizeStored(any(UUID.class), anyString(), anyInt(), any(UUID.class)))
                .thenReturn(Optional.of(stored));

        PoAttachment result = service.upload(userId, orderId, file(VALID_XLSX_BYTES));

        assertThat(result.status()).isEqualTo(PoAttachmentStatus.STORED);
        ArgumentCaptor<PoAttachment> claimCaptor = ArgumentCaptor.forClass(PoAttachment.class);
        verify(poAttachmentRepository).save(claimCaptor.capture());
        PoAttachment claim = claimCaptor.getValue();
        assertThat(claim.status()).isEqualTo(PoAttachmentStatus.PENDING);
        assertThat(claim.storageKey()).isEqualTo("po/" + orderId + "/" + claim.id() + ".xlsx");
        verify(objectStorage).store(eq(claim.storageKey()), eq(VALID_XLSX_BYTES), anyString());
    }

    @Test
    void upload_storageFails_claimStaysPending() {
        when(objectStorage.store(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("S3 down"));

        assertThatThrownBy(() -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)))
                .isInstanceOf(IllegalStateException.class);
        verify(poAttachmentRepository).save(any(PoAttachment.class)); // durable claim persisted
        verify(poAttachmentRepository, never()).finalizeStored(any(), anyString(), anyInt(), any());
    }

    @Test
    void upload_uniqueRaceLoser_surfacesPendingWinner() {
        when(poAttachmentRepository.save(any(PoAttachment.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));
        when(poAttachmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(attachment(PoAttachmentStatus.PENDING)));

        assertThatThrownBy(() -> service.upload(userId, orderId, file(VALID_XLSX_BYTES)))
                .isInstanceOf(PoUploadInProgressException.class);
        verify(objectStorage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void upload_finalizeLost_returnsExisting() {
        PoAttachment stored = attachment(PoAttachmentStatus.PENDING).stored();
        when(poAttachmentRepository.finalizeStored(any(), anyString(), anyInt(), any()))
                .thenReturn(Optional.empty());
        when(poAttachmentRepository.findById(any(UUID.class))).thenReturn(Optional.of(stored));

        PoAttachment result = service.upload(userId, orderId, file(VALID_XLSX_BYTES));

        assertThat(result.status()).isEqualTo(PoAttachmentStatus.STORED);
    }

    @Test
    void upload_fileValidation() {
        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "po.xlsx", null, new byte[0])))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("EMPTY_FILE");

        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "po.pdf", null, VALID_XLSX_BYTES)))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_FILE_NAME");

        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "../po.xlsx", null, VALID_XLSX_BYTES)))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_FILE_NAME");

        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "po.xlsx", "text/plain", VALID_XLSX_BYTES)))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_CONTENT_TYPE");

        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "po.xlsx", null, "not a zip".getBytes())))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_CONTENT_TYPE");
    }

    @Test
    void upload_oversizeRejected() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        big[0] = 0x50; big[1] = 0x4B; big[2] = 0x03; big[3] = 0x04;

        assertThatThrownBy(() -> service.upload(userId, orderId,
                new MockMultipartFile("file", "po.xlsx", null, big)))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void retry_happyPath_reusesSameClaimAndKey() {
        PoAttachment pending = attachment(PoAttachmentStatus.PENDING);
        when(poAttachmentRepository.findByIdForUpdate(pending.id())).thenReturn(Optional.of(pending));
        when(poAttachmentRepository.findById(pending.id())).thenReturn(Optional.of(pending));
        when(poAttachmentRepository.finalizeStored(eq(pending.id()), anyString(), anyInt(), eq(userId)))
                .thenReturn(Optional.of(pending.stored()));

        PoAttachmentService.RetryOutcome outcome =
                service.retry(userId, orderId, pending.id(), file(VALID_XLSX_BYTES));

        assertThat(outcome.finalizedNow()).isTrue();
        assertThat(outcome.attachment().status()).isEqualTo(PoAttachmentStatus.STORED);
        verify(objectStorage).store(eq(pending.storageKey()), eq(VALID_XLSX_BYTES), anyString());
    }

    @Test
    void retry_finalizeLost_returnsExistingWithoutOverwritingResult() {
        PoAttachment pending = attachment(PoAttachmentStatus.PENDING);
        when(poAttachmentRepository.findByIdForUpdate(pending.id())).thenReturn(Optional.of(pending));
        when(poAttachmentRepository.findById(pending.id()))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(pending.stored()));
        when(poAttachmentRepository.finalizeStored(any(), anyString(), anyInt(), any()))
                .thenReturn(Optional.empty());

        PoAttachmentService.RetryOutcome outcome =
                service.retry(userId, orderId, pending.id(), file(VALID_XLSX_BYTES));

        assertThat(outcome.finalizedNow()).isFalse();
        assertThat(outcome.attachment().status()).isEqualTo(PoAttachmentStatus.STORED);
    }

    @Test
    void retry_onStoredClaim_409() {
        PoAttachment stored = attachment(PoAttachmentStatus.STORED);
        when(poAttachmentRepository.findByIdForUpdate(stored.id())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.retry(userId, orderId, stored.id(), file(VALID_XLSX_BYTES)))
                .isInstanceOf(PoAttachmentExistsException.class);
        verify(objectStorage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void retry_wrongOrder_404() {
        UUID foreignOrderId = UUID.randomUUID();
        PoAttachment foreign = new PoAttachment(UUID.randomUUID(), foreignOrderId,
                "po/" + foreignOrderId + "/x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                VALID_XLSX_BYTES.length, userId, PoAttachmentStatus.PENDING, null, null);
        when(poAttachmentRepository.findByIdForUpdate(foreign.id())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.retry(userId, orderId, foreign.id(), file(VALID_XLSX_BYTES)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ATTACHMENT_NOT_FOUND");
        verify(objectStorage, never()).store(anyString(), any(), anyString());
    }

    private PoAttachment attachment(PoAttachmentStatus status) {
        UUID id = UUID.randomUUID();
        return new PoAttachment(id, orderId, "po/" + orderId + "/" + id + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                VALID_XLSX_BYTES.length, userId, status, Instant.now(), Instant.now());
    }
}
