package com.builddash.backend.domain.model;

import java.util.UUID;

public record PricingRequest(
        UUID productId,
        int quantity,
        UUID userId,
        String couponCode
) {
}
