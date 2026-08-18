package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ProductSearchHitResponse")
public record ProductSearchHitResponse(
        UUID productId,
        String name,
        String category,
        String brand,
        String stockStatus
) {
}
