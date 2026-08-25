package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryStatusUpdateRequest(
        @NotNull(message = "status is required")
        OrderStatus status,
        String driverId,
        String driverPhone,
        Double latitude,
        Double longitude
) {}
