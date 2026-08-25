package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.OrderTracking;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderTrackingService {

    void updateDeliveryStatus(UUID orderId, OrderStatus status, String driverId, String driverPhone, Double latitude, Double longitude, String apiKey);

    OrderTracking getTracking(UUID userId, UUID orderId);

    void rescheduleOrder(UUID userId, UUID orderId, UUID newSlotId, LocalDate newSlotDate);

    void cancelOrderWithinWindow(UUID userId, UUID orderId);

    void callDriver(UUID userId, UUID orderId);
}
