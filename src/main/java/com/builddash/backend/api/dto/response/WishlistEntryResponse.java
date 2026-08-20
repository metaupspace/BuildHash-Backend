package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "WishlistEntryResponse")
public record WishlistEntryResponse(
        UUID productId,
        Instant createdAt
) {
}
