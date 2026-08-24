package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DeliveryTrackingEvent {

    private UUID id;
    private UUID orderId;
    private OrderStatus status;
    private Double latitude;
    private Double longitude;
    private Instant recordedAt;
    private Instant createdAt;

    public DeliveryTrackingEvent(UUID id, UUID orderId, OrderStatus status, Double latitude, Double longitude, Instant recordedAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
        this.createdAt = Instant.now();
    }
}
