package com.builddash.backend.domain.model;

import java.util.UUID;

/** No price field — Phase 2 (Pricing) doesn't exist yet, same reason catalog listing omits it. */
public record ProductSearchHit(
        UUID productId,
        String name,
        String category,
        String brand,
        String stockStatus
) {
}
