package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.Vendor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String name,
        List<UUID> categoryIds,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(vendor.id(), vendor.name(), vendor.categoryIds(),
                vendor.active(), vendor.createdAt(), vendor.updatedAt());
    }
}
