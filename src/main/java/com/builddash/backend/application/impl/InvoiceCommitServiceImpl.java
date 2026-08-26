package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.application.service.InvoiceCommitService;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceCommitServiceImpl implements InvoiceCommitService {

    private final InvoiceRepository invoiceRepository;
    private final GstSequenceService gstSequenceService;

    @Override
    @Transactional
    public Invoice commit(UUID invoiceId, String storageKey) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found: " + invoiceId));

        if (invoice.status() == InvoiceStatus.READY) {
            log.info("Invoice {} is already READY, skipping commit", invoiceId);
            return invoice;
        }

        String invoiceNumber = gstSequenceService.nextNumber(GstSequenceType.INVOICE);
        Invoice ready = invoice.markReady(invoiceNumber, storageKey);
        Invoice saved = invoiceRepository.save(ready);

        log.info("Committed invoice {} with number {} (status READY)", invoiceId, invoiceNumber);
        return saved;
    }
}
