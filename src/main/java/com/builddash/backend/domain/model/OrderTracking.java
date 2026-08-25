package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderTracking(
        UUID orderId,
        OrderStatus status,
        String driverId,
        String driverPhone,
        Double latitude,
        Double longitude,
        Instant updatedAt
) {}
