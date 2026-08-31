package com.builddash.backend.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * companyId names the target company — it is never trusted: B2bAuthorizer
 * resolves the caller's membership and permission for that company against
 * current database state (non-member -> 404).
 */
public record RfqCreateRequest(
        @NotNull UUID companyId,
        @NotNull Instant expiresAt,
        String notes,
        @NotEmpty @Valid List<Item> items
) {

    public record Item(
            @NotNull UUID productId,
            @Positive int quantity
    ) {
    }
}
