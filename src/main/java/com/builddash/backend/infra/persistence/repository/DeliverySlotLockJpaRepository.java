package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.infra.persistence.entity.DeliverySlotLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverySlotLockJpaRepository extends JpaRepository<DeliverySlotLockEntity, UUID> {

    @Query("SELECT l FROM DeliverySlotLockEntity l WHERE l.userId = :userId AND l.status = 'ACTIVE' AND l.expiresAt > :asOf ORDER BY l.createdAt DESC LIMIT 1")
    Optional<DeliverySlotLockEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("asOf") Instant asOf);

    @Query("SELECT l FROM DeliverySlotLockEntity l WHERE l.status = 'ACTIVE' AND l.expiresAt <= :asOf")
    List<DeliverySlotLockEntity> findExpiredActiveLocks(@Param("asOf") Instant asOf);

    @Modifying
    @Query("UPDATE DeliverySlotLockEntity l SET l.status = :to, l.updatedAt = CURRENT_TIMESTAMP WHERE l.id = :lockId AND l.status = :from")
    int tryTransitionStatus(@Param("lockId") UUID lockId, @Param("from") DeliverySlotLockStatus from,
                             @Param("to") DeliverySlotLockStatus to);

    @Query("SELECT l FROM DeliverySlotLockEntity l WHERE l.userId = :userId AND l.status = 'ACTIVE'")
    List<DeliverySlotLockEntity> findAllActiveByUserId(@Param("userId") UUID userId);

    void deleteByUserId(UUID userId);
}
