package com.builddash.backend.domain.model;

import java.util.List;
import java.util.UUID;

public record Cart(
        UUID id,
        UUID userId,
        UUID projectId,
        String appliedCartCoupon,
        List<CartLineItem> items
) {
}
