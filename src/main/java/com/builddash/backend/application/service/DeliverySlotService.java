package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.DeliverySlotOption;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DeliverySlotService {

    List<DeliverySlotOption> getAvailableSlots(LocalDate date);

    DeliverySlotLock acquireOrSwapLock(UUID userId, UUID slotId, LocalDate date, Duration ttl);

    /**
     * H2.6: plain acquire with no "swap away the user's other active lock" detection —
     * for approval resume, where the gated order holds no lock of its own (already
     * released by openApproval) so there is no legitimate prior lock to swap away from.
     * Never inspects or touches any other lock the user may hold.
     */
    DeliverySlotLock acquireLock(UUID userId, UUID slotId, LocalDate date, Duration ttl);

    void releaseLock(UUID lockId, UUID userId);

    /**
     * Marks an ACTIVE lock CONSUMED on payment success. Unlike release, the counter
     * is NOT decremented — the confirmed order still occupies delivery capacity.
     * (Releasing on success let a capacity-N slot sell more than N deliveries.)
     * Returns false (H2.7) when the lock was not ACTIVE at the moment of the attempt —
     * a caller must treat that as an anomaly, not a silent success.
     */
    boolean consumeLock(UUID lockId, UUID userId);

    DeliverySlotLock swapConsumedLock(UUID userId, UUID oldLockId, UUID oldSlotId, LocalDate oldSlotDate, UUID newSlotId, LocalDate newSlotDate);

    void releaseConsumedLock(UUID lockId, UUID slotId, LocalDate slotDate);
}
