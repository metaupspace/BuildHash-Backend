package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.OrderStatus;

import java.time.Instant;

public record OrderTrackingResponse(
        OrderStatus status,
        DriverDto driver,
        LocationDto location,
        Instant updatedAt
) {}
