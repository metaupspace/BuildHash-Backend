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

    /**
     * H2.4/H2.7: compare-and-swap status transition. Returns rows affected (0 or 1) so
     * callers can decrement capacity only on an actual transition, not on every call —
     * this is what makes release/consume/expiry/deletion-cleanup idempotent against a
     * concurrent writer racing the same lock.
     */
    int tryTransitionStatus(UUID lockId, DeliverySlotLockStatus from, DeliverySlotLockStatus to);

    /** H2.8: every ACTIVE lock a user holds — not just the most recent one, since H2.6
     *  lets a user legitimately hold more than one concurrently. */
    List<DeliverySlotLock> findAllActiveByUserId(UUID userId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)): transient rows, swept with the account anyway. */
    void deleteByUserId(UUID userId);
}
