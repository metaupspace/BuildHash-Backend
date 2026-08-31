package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, UUID> {

    List<DeviceEntity> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    Optional<DeviceEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update DeviceEntity d set d.revokedAt = :now where d.userId = :userId and d.revokedAt is null")
    void revokeAllActiveByUserId(UUID userId, Instant now);

    java.util.List<DeviceEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
