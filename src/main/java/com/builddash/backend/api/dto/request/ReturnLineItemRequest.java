package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnLineItemRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity requested must be at least 1")
        Integer quantity
) {}
