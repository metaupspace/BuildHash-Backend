package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Partial PATCH: null fields leave the stored value unchanged. */
public record VendorUpdateRequest(
        @Size(max = 200) String name,
        List<UUID> categoryIds,
        Boolean active
) {
}
