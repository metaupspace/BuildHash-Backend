package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Invoice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    Optional<Invoice> findByIdForUpdate(UUID id);
    Optional<Invoice> findByOrderId(UUID orderId);
    List<Invoice> findSchedulerClaimableInvoices(int maxAttempts, Instant cutoff);
    List<Invoice> findDlqClaimableInvoices(int maxAttempts, Instant cutoff);
}
