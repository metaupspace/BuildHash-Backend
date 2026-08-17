package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "DeviceResponse")
public record DeviceResponse(
        UUID id,
        @Schema(example = "iPhone 15 - Safari") String deviceFingerprint,
        Instant lastSeenAt,
        Instant createdAt
) {
}
