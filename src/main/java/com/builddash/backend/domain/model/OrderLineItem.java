package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineItem(
        UUID id,
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxAmount,
        /** Charged line total, GST included. unitPrice×quantity drifts by paise (unitPrice = total/qty rounded). */
        BigDecimal lineTotal,
        BigDecimal taxRatePercent
) {
    public OrderLineItem(UUID id, UUID productId, int quantity, BigDecimal unitPrice, BigDecimal taxAmount, BigDecimal lineTotal) {
        this(id, productId, quantity, unitPrice, taxAmount, lineTotal, null);
    }
}
