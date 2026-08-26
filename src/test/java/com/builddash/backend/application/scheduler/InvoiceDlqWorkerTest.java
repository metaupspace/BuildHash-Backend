package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.InvoiceGenerationService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDlqWorkerTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceGenerationService invoiceGenerationService;

    private InvoiceDlqWorker dlqWorker;

    @BeforeEach
    void setUp() {
        dlqWorker = new InvoiceDlqWorker(invoiceRepository, invoiceGenerationService);
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
    void onDlqMessage_validInvoiceId_triggersInvoiceGeneration() {
        UUID invoiceId = UUID.randomUUID();
        dlqWorker.onDlqMessage(invoiceId.toString());

        verify(invoiceGenerationService).processInvoice(invoiceId);
    }

    @Test
    void onDlqMessage_emptyOrNull_ignored() {
        dlqWorker.onDlqMessage(null);
        dlqWorker.onDlqMessage("   ");

        verify(invoiceGenerationService, never()).processInvoice(any());
    }

    @Test
    void runTwoHourDlqSweep_processesAllDlqClaimableInvoices() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Invoice inv1 = sampleInvoice(id1, InvoiceStatus.DLQ_RETRY, 3);
        Invoice inv2 = sampleInvoice(id2, InvoiceStatus.GENERATING, 4);

        when(invoiceRepository.findDlqClaimableInvoices(eq(3), any(Instant.class)))
                .thenReturn(List.of(inv1, inv2));

        dlqWorker.runTwoHourDlqSweep();

        verify(invoiceGenerationService).processInvoice(id1);
        verify(invoiceGenerationService).processInvoice(id2);
    }
}
