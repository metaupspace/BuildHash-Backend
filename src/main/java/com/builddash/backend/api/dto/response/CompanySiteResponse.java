package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.CompanySite;

import java.time.Instant;
import java.util.UUID;

public record CompanySiteResponse(
        UUID id,
        String name,
        UUID addressId,
        boolean active,
        Instant createdAt
) {

    public static CompanySiteResponse from(CompanySite site) {
        return new CompanySiteResponse(site.id(), site.name(), site.addressId(), site.active(),
                site.createdAt());
    }
}
