package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.OrderTracking;

import java.util.UUID;

public interface OrderTrackingBroadcaster {
    void broadcast(UUID orderId, OrderTracking tracking);
}
