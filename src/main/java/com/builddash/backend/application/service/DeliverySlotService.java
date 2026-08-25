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

    void releaseLock(UUID lockId, UUID userId);

    /**
     * Marks an ACTIVE lock CONSUMED on payment success. Unlike release, the counter
     * is NOT decremented — the confirmed order still occupies delivery capacity.
     * (Releasing on success let a capacity-N slot sell more than N deliveries.)
     */
    void consumeLock(UUID lockId, UUID userId);

    DeliverySlotLock swapConsumedLock(UUID userId, UUID oldLockId, UUID oldSlotId, LocalDate oldSlotDate, UUID newSlotId, LocalDate newSlotDate);

    void releaseConsumedLock(UUID lockId, UUID slotId, LocalDate slotDate);
}
