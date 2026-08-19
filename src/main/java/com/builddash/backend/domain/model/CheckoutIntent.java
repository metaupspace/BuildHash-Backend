package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CheckoutIntent(
        UUID id,
        UUID userId,
        UUID cartId,
        UUID addressId,
        UUID slotId,
        LocalDate slotDate,
        BigDecimal lockedTotal,
        Instant expiresAt,
        PricedCart pricedCart
) {
}
