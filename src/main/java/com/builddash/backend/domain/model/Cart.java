package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CartType;

import java.util.List;
import java.util.UUID;

public record Cart(
        UUID id,
        UUID userId,
        UUID projectId,
        CartType type,
        String appliedCartCoupon,
        List<CartLineItem> items
) {
}
