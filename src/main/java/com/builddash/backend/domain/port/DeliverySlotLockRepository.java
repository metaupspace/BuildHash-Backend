package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.model.DeliverySlotLock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverySlotLockRepository {
    DeliverySlotLock save(DeliverySlotLock lock);
    Optional<DeliverySlotLock> findActiveByUserId(UUID userId, Instant asOf);
    Optional<DeliverySlotLock> findById(UUID lockId);
    List<DeliverySlotLock> findExpiredActiveLocks(Instant asOf);
    void updateStatus(UUID lockId, DeliverySlotLockStatus status);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)): transient rows, swept with the account anyway. */
    void deleteByUserId(UUID userId);
}
