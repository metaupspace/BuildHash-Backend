package com.builddash.backend.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Device {

    private UUID id;
    private UUID userId;
    private String refreshTokenHash;
    private String deviceFingerprint;
    private Instant lastSeenAt;
    private Instant createdAt;
    private Instant revokedAt;
}
