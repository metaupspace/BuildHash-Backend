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
}
