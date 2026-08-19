package com.builddash.backend.domain.model;

import java.util.UUID;

public record Address(
        UUID id,
        UUID userId,
        String type, // HOME, SITE, WORK, etc.
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
