package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.RfqQuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A vendor's quote on an RFQ: one total, one validity horizon, no line-level
 * prices (locked 9-B). UNIQUE (rfqId, vendorId) is enforced by the database —
 * one quote per vendor per RFQ. Expired quotes are retained historically but
 * can no longer be selected for conversion.
 */
public record RfqQuote(
        UUID id,
        UUID rfqId,
        UUID vendorId,
        BigDecimal totalAmount,
        Instant validUntil,
        RfqQuoteStatus status,
        Instant submittedAt
) {

    public boolean expired(Instant now) {
        return !validUntil.isAfter(now);
    }
}
