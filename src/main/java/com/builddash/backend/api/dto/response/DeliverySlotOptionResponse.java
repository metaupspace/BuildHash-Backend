package com.builddash.backend.api.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DeliverySlotOptionResponse(
        UUID slotId,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate date,
        int capacity,
        int availableCount
) {
}
