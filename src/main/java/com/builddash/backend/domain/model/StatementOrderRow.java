package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Per-order aggregation row (9-E): the PDF renders one line per order, never per line-item. */
public record StatementOrderRow(
        UUID orderId,
        UUID siteId,
        Instant confirmedAt,
        BigDecimal netTotal,
        BigDecimal taxTotal,
        BigDecimal grossTotal,
        String invoiceStatus
) {
}
