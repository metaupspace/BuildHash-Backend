package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpsertCartItemRequest(
        @NotNull UUID productId,
        @Min(0) int quantity,
        String itemCoupon
) {
}
