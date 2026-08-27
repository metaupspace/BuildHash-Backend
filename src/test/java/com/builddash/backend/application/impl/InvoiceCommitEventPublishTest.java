package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 Checkpoint A event-publish proofs for InvoiceCommitServiceImpl: a real transition to
 * READY fires InvoiceReadyEvent(orderId); the already-READY early return fires nothing.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceCommitEventPublishTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private GstSequenceService gstSequenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InvoiceCommitServiceImpl commitService;

    @BeforeEach
    void setUp() {
        commitService = new InvoiceCommitServiceImpl(invoiceRepository, gstSequenceService, eventPublisher);
    }

    @Test
    void commit_firesInvoiceReadyEvent() {
        UUID orderId = UUID.randomUUID();
        Invoice generating = new Invoice(UUID.randomUUID(), orderId, null, InvoiceStatus.GENERATING,
                null, "application/pdf", null, 1, Instant.now(), Instant.now());
        when(invoiceRepository.findByIdForUpdate(generating.id())).thenReturn(Optional.of(generating));
        when(gstSequenceService.nextNumber(GstSequenceType.INVOICE)).thenReturn("INV-000042");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        commitService.commit(generating.id(), "invoices/test.pdf");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        InvoiceReadyEvent event = (InvoiceReadyEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(orderId);
    }

    @Test
    void commit_alreadyReady_firesNothing() {
        UUID orderId = UUID.randomUUID();
        Invoice ready = new Invoice(UUID.randomUUID(), orderId, "INV-000041", InvoiceStatus.READY,
                "invoices/existing.pdf", "application/pdf", Instant.now(), 1, Instant.now(), Instant.now());
        when(invoiceRepository.findByIdForUpdate(ready.id())).thenReturn(Optional.of(ready));

        Invoice result = commitService.commit(ready.id(), "invoices/other.pdf");

        assertThat(result.status()).isEqualTo(InvoiceStatus.READY);
        verify(invoiceRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
