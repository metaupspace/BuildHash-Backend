package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.InvoiceStatus;

import java.time.Instant;

public record InvoiceResponse(
        InvoiceStatus status,
        String invoiceNumber,
        String url,
        Instant expiresAt
) {}
