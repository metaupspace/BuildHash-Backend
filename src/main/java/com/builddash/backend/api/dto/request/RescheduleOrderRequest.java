package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RescheduleOrderRequest(
        @NotNull(message = "newSlotId is required")
        UUID newSlotId,
        @NotNull(message = "slotDate is required")
        LocalDate slotDate
) {}
