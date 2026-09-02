package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.InvoiceClaimService;
import com.builddash.backend.application.service.InvoiceCommitService;
import com.builddash.backend.application.service.InvoiceGenerationService;
import com.builddash.backend.application.service.InvoiceSnapshotBuilder;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.port.InvoiceRenderer;
import com.builddash.backend.domain.port.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceGenerationServiceImpl implements InvoiceGenerationService {

    private final InvoiceClaimService invoiceClaimService;
    private final InvoiceSnapshotBuilder snapshotBuilder;
    private final InvoiceRenderer invoiceRenderer;
    private final ObjectStorage objectStorage;
    private final InvoiceCommitService invoiceCommitService;

    @Override
    public void processInvoice(UUID invoiceId) {
        // Phase 1: Claim phase (short transaction inside InvoiceClaimService)
        Invoice claimed = invoiceClaimService.claim(invoiceId);
        if (claimed.status() == InvoiceStatus.READY || claimed.status() == InvoiceStatus.FAILED) {
            return;
        }

        // Phase 2: Slow I/O phase (NO transaction)
        OrderInvoiceSnapshot snapshot = snapshotBuilder.build(claimed.orderId());
        byte[] pdfBytes = invoiceRenderer.render(snapshot);
        String storageKey = "invoices/" + claimed.orderId() + "/" + claimed.id() + ".pdf";
        objectStorage.store(storageKey, pdfBytes, "application/pdf");

        // Phase 3: Commit phase (short transaction inside InvoiceCommitService)
        invoiceCommitService.commit(claimed.id(), storageKey);
    }
}
