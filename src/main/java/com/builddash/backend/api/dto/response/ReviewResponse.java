package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ReviewResponse")
public record ReviewResponse(
        UUID id,
        UUID productId,
        UUID userId,
        int rating,
        String comment,
        Instant createdAt
) {
}
