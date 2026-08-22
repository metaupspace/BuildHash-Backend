package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.DeliveryTrackingEvent;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryTrackingEventRepository {
    DeliveryTrackingEvent save(DeliveryTrackingEvent event);
    Optional<DeliveryTrackingEvent> findLatestByOrderId(UUID orderId);
}
