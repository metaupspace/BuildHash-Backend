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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceClaimServiceImpl implements InvoiceClaimService {

    public static final int MAX_TOTAL_ATTEMPTS = 6;
    private static final Duration STALENESS_THRESHOLD = Duration.ofMinutes(15);

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public Invoice claim(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found: " + invoiceId));

        if (invoice.status() == InvoiceStatus.READY || invoice.status() == InvoiceStatus.FAILED) {
            log.info("Invoice {} is already terminal ({}), skipping claim", invoiceId, invoice.status());
            return invoice;
        }

        // H5.5: Fresh GENERATING claim guard (< 15 min old). Another worker is actively generating;
        // do not bump attempt count or create duplicate work.
        if (invoice.status() == InvoiceStatus.GENERATING && invoice.updatedAt() != null
                && invoice.updatedAt().isAfter(Instant.now().minus(STALENESS_THRESHOLD))) {
            log.info("Invoice {} is already actively GENERATING (< 15m old), returning existing claim", invoiceId);
            return invoice;
        }

        // H5.3: Bounded attempts cap: if attempts exhausted, transition to terminal FAILED.
        if (invoice.attemptCount() >= MAX_TOTAL_ATTEMPTS) {
            log.error("Invoice {} exhausted max attempts ({}), marking FAILED", invoiceId, invoice.attemptCount());
            Invoice failed = invoice.markFailed();
            return invoiceRepository.save(failed);
        }

        Invoice claimed = invoice.claim();
        Invoice saved = invoiceRepository.save(claimed);
        log.info("Claimed invoice {} for generation (attempt {})", invoiceId, saved.attemptCount());
        return saved;
    }
}
