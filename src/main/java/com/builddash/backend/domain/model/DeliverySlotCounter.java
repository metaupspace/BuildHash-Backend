package com.builddash.backend.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record DeliverySlotCounter(
        UUID id,
        UUID slotId,
        LocalDate slotDate,
        int capacity,
        int currentCount
) {
    public boolean hasCapacity() {
        return currentCount < capacity;
    }

    public DeliverySlotCounter increment() {
        return new DeliverySlotCounter(id, slotId, slotDate, capacity, currentCount + 1);
    }

    public DeliverySlotCounter decrement() {
        return new DeliverySlotCounter(id, slotId, slotDate, capacity, Math.max(0, currentCount - 1));
    }
}
