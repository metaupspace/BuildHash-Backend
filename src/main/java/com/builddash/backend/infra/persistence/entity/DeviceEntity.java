package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Id is assigned by the caller (not DB/Hibernate-generated) because the same UUID must be
 * embedded as the {@code deviceId} claim in the refresh JWT before this row is persisted.
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class DeviceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "refresh_token_hash")
    private String refreshTokenHash;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        lastSeenAt = createdAt;
    }
}
