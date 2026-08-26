package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.InvoiceGenerationService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.infra.config.InvoiceQueueConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceGenerationSchedulerTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceGenerationService invoiceGenerationService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private InvoiceGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InvoiceGenerationScheduler(invoiceRepository, invoiceGenerationService, rabbitTemplate);
    }

    private Invoice sampleInvoice(UUID id, InvoiceStatus status, int attemptCount) {
        return new Invoice(
                id,
                UUID.randomUUID(),
                null,
                status,
                null,
                "application/pdf",
                null,
                attemptCount,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void runGenerationCycle_processesClaimableInvoicesSuccessfully() {
        UUID id = UUID.randomUUID();
        Invoice invoice = sampleInvoice(id, InvoiceStatus.PENDING, 0);

        when(invoiceRepository.findSchedulerClaimableInvoices(eq(3), any(Instant.class)))
                .thenReturn(List.of(invoice));

        scheduler.runGenerationCycle();

        verify(invoiceGenerationService).processInvoice(id);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void runGenerationCycle_whenGenerationFailsAndAttemptsReach3_transitionsToDlqRetryAndPublishesToRabbit() {
        UUID id = UUID.randomUUID();
        Invoice invoice = sampleInvoice(id, InvoiceStatus.PENDING, 2);
        Invoice invoiceAfterClaim = sampleInvoice(id, InvoiceStatus.GENERATING, 3);

        when(invoiceRepository.findSchedulerClaimableInvoices(eq(3), any(Instant.class)))
                .thenReturn(List.of(invoice));

        doThrow(new RuntimeException("OpenPDF font rendering error"))
                .when(invoiceGenerationService).processInvoice(id);

        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoiceAfterClaim));

        scheduler.runGenerationCycle();

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(InvoiceStatus.DLQ_RETRY);

        verify(rabbitTemplate).convertAndSend(
                eq(InvoiceQueueConfig.DLX_NAME),
                eq(InvoiceQueueConfig.DLQ_NAME),
                eq(id.toString())
        );
    }

    @Test
    void runGenerationCycle_whenGenerationFailsAndAttemptsUnder3_doesNotPublishToRabbit() {
        UUID id = UUID.randomUUID();
        Invoice invoice = sampleInvoice(id, InvoiceStatus.PENDING, 0);
        Invoice invoiceAfterClaim = sampleInvoice(id, InvoiceStatus.GENERATING, 1);

        when(invoiceRepository.findSchedulerClaimableInvoices(eq(3), any(Instant.class)))
                .thenReturn(List.of(invoice));

        doThrow(new RuntimeException("Transient S3 network timeout"))
                .when(invoiceGenerationService).processInvoice(id);

        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoiceAfterClaim));

        scheduler.runGenerationCycle();

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
