package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.InvoiceStatus;

import java.time.Instant;
import java.util.UUID;

public interface InvoiceQueryService {

    record InvoiceQueryResult(
            InvoiceStatus status,
            String invoiceNumber,
            String url,
            Instant expiresAt
    ) {}

    InvoiceQueryResult getInvoice(UUID userId, UUID orderId);
}
