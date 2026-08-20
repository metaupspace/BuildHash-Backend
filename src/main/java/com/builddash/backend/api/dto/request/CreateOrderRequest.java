package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID addressId,
        @NotNull UUID slotId,
        @NotNull LocalDate slotDate,
        BigDecimal expectedTotal
) {
}
