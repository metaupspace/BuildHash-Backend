package com.builddash.backend.domain.model;

import java.util.UUID;

public record CartLineItem(
        UUID id,
        UUID cartId,
        UUID productId,
        int quantity,
        String appliedItemCoupon
) {
}
