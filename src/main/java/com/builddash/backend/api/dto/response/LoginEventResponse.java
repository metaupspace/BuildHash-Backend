package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.LoginEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "LoginEventResponse")
public record LoginEventResponse(
        UUID id,
        LoginEventType eventType,
        @Schema(example = "49.36.128.14") String ipAddress,
        @Schema(example = "iPhone 15 - Safari") String deviceFingerprint,
        Instant createdAt
) {
}
