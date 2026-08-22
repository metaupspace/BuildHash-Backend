package com.builddash.backend.domain.model;

import java.time.LocalTime;
import java.util.UUID;

public record SlotConfiguration(
        UUID id,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        boolean isActive
) {
}
