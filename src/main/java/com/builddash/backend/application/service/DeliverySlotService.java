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
}
