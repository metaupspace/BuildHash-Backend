package com.builddash.backend.api.dto.response;

import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.domain.model.RfqQuote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Comparison row: vendor info, validity horizon and the computed expired flag (expired quotes stay listed). */
public record RfqQuoteResponse(
        UUID id,
        UUID rfqId,
        UUID vendorId,
        String vendorName,
        BigDecimal totalAmount,
        Instant validUntil,
        String status,
        boolean expired,
        Instant submittedAt
) {

    public static RfqQuoteResponse from(RfqService.QuoteComparison comparison) {
        RfqQuote quote = comparison.quote();
        return new RfqQuoteResponse(
                quote.id(),
                quote.rfqId(),
                quote.vendorId(),
                comparison.vendor() != null ? comparison.vendor().name() : null,
                quote.totalAmount(),
                quote.validUntil(),
                quote.status().name(),
                comparison.expired(),
                quote.submittedAt());
    }
}
