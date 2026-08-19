package com.builddash.backend.api.dto.response;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID userId,
        String type,
        String line1,
        String line2,
        String city,
        String state,
        String zipCode,
        Double lat,
        Double lng,
        boolean isServiceable
) {
}
