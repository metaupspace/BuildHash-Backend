package com.builddash.backend.domain.model;

import java.util.UUID;

public record IdempotencyKey(
        String key,
        UUID orderId
) {
}
