package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWishlistItemRequest(
        @NotNull
        @Schema(example = "3b0a4b8e-6b1e-4e9a-9f5c-1a2b3c4d5e6f")
        UUID productId
) {
}
