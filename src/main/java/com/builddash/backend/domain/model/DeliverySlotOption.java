package com.builddash.backend.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DeliverySlotOption(
        UUID slotId,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate date,
        int capacity,
        int availableCount
) {
}
