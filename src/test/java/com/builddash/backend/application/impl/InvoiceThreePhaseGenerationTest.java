package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.application.service.InvoiceClaimService;
import com.builddash.backend.application.service.InvoiceCommitService;
import com.builddash.backend.application.service.InvoiceSnapshotBuilder;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.port.InvoiceRenderer;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceThreePhaseGenerationTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceSnapshotBuilder snapshotBuilder;

    @Mock
    private InvoiceRenderer invoiceRenderer;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private GstSequenceService gstSequenceService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private InvoiceClaimService claimService;
    private InvoiceCommitService commitService;
    private InvoiceGenerationServiceImpl generationService;

    private Invoice currentInvoice;
    private AtomicLong sequenceCounter;

    @BeforeEach
    void setUp() {
        claimService = new InvoiceClaimServiceImpl(invoiceRepository);
        commitService = new InvoiceCommitServiceImpl(invoiceRepository, gstSequenceService, eventPublisher);
        generationService = new InvoiceGenerationServiceImpl(
                claimService,
                snapshotBuilder,
                invoiceRenderer,
                objectStorage,
                commitService
        );

        sequenceCounter = new AtomicLong(0);
    }

    private OrderInvoiceSnapshot sampleSnapshot(UUID orderId) {
        return new OrderInvoiceSnapshot(
                orderId,
                null,
                Instant.now(),
                "+919876543210",
                "Site 42, Bengaluru",
                List.of(),
                new BigDecimal("1000.00"),
                new BigDecimal("180.00"),
                new BigDecimal("1180.00")
        );
    }

    @Test
    void failedRenderOrUpload_doesNotBurnSequenceNumber_subsequentAttemptReceivesFirstGaplessNumber() {
        UUID invoiceId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        currentInvoice = new Invoice(
                invoiceId,
                orderId,
                null,
                InvoiceStatus.PENDING,
                null,
                "application/pdf",
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenAnswer(inv -> Optional.of(currentInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            currentInvoice = inv.getArgument(0);
            return currentInvoice;
        });

        OrderInvoiceSnapshot snapshot = sampleSnapshot(orderId);
        when(snapshotBuilder.build(orderId)).thenReturn(snapshot);
        when(invoiceRenderer.render(snapshot)).thenReturn(new byte[]{1, 2, 3});

        // Attempt 1: S3 upload fails
        when(objectStorage.store(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated S3 connection timeout"));

        assertThatThrownBy(() -> generationService.processInvoice(invoiceId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated S3 connection timeout");

        // Assert Phase 1 claimed but Phase 3 never ran
        assertThat(currentInvoice.status()).isEqualTo(InvoiceStatus.GENERATING);
        assertThat(currentInvoice.attemptCount()).isEqualTo(1);
        assertThat(currentInvoice.number()).isNull();
        verify(gstSequenceService, never()).nextNumber(any());

        // Attempt 2: Stale claim reclaimed by scheduler after 15m and S3 upload succeeds
        currentInvoice = new Invoice(currentInvoice.id(), currentInvoice.orderId(), currentInvoice.number(),
                currentInvoice.status(), currentInvoice.storageKey(), currentInvoice.contentType(),
                currentInvoice.generatedAt(), currentInvoice.attemptCount(), currentInvoice.createdAt(),
                Instant.now().minus(java.time.Duration.ofMinutes(16)));
        doReturn("invoices/" + orderId + "/" + invoiceId + ".pdf")
                .when(objectStorage).store(any(), any(), any());
        when(gstSequenceService.nextNumber(GstSequenceType.INVOICE))
                .thenAnswer(inv -> String.format("INV-2627-%06d", sequenceCounter.incrementAndGet()));

        generationService.processInvoice(invoiceId);

        // Assert Phase 3 committed with the first gapless sequence number
        assertThat(currentInvoice.status()).isEqualTo(InvoiceStatus.READY);
        assertThat(currentInvoice.attemptCount()).isEqualTo(2);
        assertThat(currentInvoice.number()).isEqualTo("INV-2627-000001");
        assertThat(sequenceCounter.get()).isEqualTo(1);
    }
}
