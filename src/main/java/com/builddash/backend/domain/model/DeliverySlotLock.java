package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.DeliverySlotLockStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeliverySlotLock(
        UUID id,
        UUID userId,
        UUID slotId,
        LocalDate slotDate,
        Instant expiresAt,
        DeliverySlotLockStatus status
) {
    public boolean isActive(Instant now) {
        return status == DeliverySlotLockStatus.ACTIVE && expiresAt.isAfter(now);
    }
}
