package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.InvoiceClaimService;
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
public class InvoiceClaimServiceImpl implements InvoiceClaimService {

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public Invoice claim(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found: " + invoiceId));

        if (invoice.status() == InvoiceStatus.READY) {
            log.info("Invoice {} is already READY, skipping claim", invoiceId);
            return invoice;
        }

        Invoice claimed = invoice.claim();
        Invoice saved = invoiceRepository.save(claimed);
        log.info("Claimed invoice {} for generation (attempt {})", invoiceId, saved.attemptCount());
        return saved;
    }
}
