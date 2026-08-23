package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CheckoutIntentResponse(
        UUID intentId,
        UUID userId,
        UUID cartId,
        UUID addressId,
        UUID slotId,
        LocalDate slotDate,
        BigDecimal lockedTotal,
        Instant expiresAt,
        PricedCartResponse pricedCart
) {
}
