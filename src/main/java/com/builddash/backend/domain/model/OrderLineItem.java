package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineItem(
        UUID id,
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxAmount
) {
}
