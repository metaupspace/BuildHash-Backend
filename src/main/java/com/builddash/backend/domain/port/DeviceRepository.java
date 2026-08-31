package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository {

    Device save(Device device);

    Optional<Device> findById(UUID id);

    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    List<Device> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    void revokeAllActiveByUserId(UUID userId, Instant now);

    /** DPDP export: every device including revoked ones — the export is the user's full record. */
    List<Device> findAllByUserId(UUID userId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)) — revoke is soft, deletion is rows gone. */
    void deleteByUserId(UUID userId);
}
