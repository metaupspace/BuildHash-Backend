package com.builddash.backend.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A supply-side vendor. No user relationship and no authentication (locked 9-B
 * scope: vendors never log in): quotes arrive through the application-ADMIN
 * submission endpoint. Categories map onto the existing catalog; deactivation
 * only blocks NEW quote submission — historical routes and quotes stay as-is.
 */
public record Vendor(
        UUID id,
        String name,
        boolean active,
        List<UUID> categoryIds,
        Instant createdAt,
        Instant updatedAt
) {

    public Vendor {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    }

    public Vendor with(String newName, List<UUID> newCategoryIds, Boolean activeOverride) {
        return new Vendor(
                id,
                newName != null ? newName : name,
                activeOverride != null ? activeOverride : active,
                newCategoryIds != null ? newCategoryIds : categoryIds,
                createdAt,
                updatedAt);
    }
}
