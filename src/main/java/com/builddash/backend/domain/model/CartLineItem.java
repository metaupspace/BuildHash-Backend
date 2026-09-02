package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CartLineItem(
        UUID id,
        UUID cartId,
        UUID productId,
        int quantity,
        String appliedItemCoupon,
        BigDecimal unitPriceOverride
) {
    public CartLineItem(UUID id, UUID cartId, UUID productId, int quantity, String appliedItemCoupon) {
        this(id, cartId, productId, quantity, appliedItemCoupon, null);
    }
}
