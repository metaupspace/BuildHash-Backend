package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.infra.persistence.entity.DeliveryTrackingEventEntity;

import java.time.Instant;

public final class DeliveryTrackingEventMapper {

    private DeliveryTrackingEventMapper() {}

    public static DeliveryTrackingEvent toDomain(DeliveryTrackingEventEntity entity) {
        if (entity == null) return null;
        return new DeliveryTrackingEvent(
                entity.getId(),
                entity.getOrderId(),
                entity.getStatus(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getRecordedAt()
        );
    }

    public static DeliveryTrackingEventEntity toEntity(DeliveryTrackingEvent domain) {
        if (domain == null) return null;
        return new DeliveryTrackingEventEntity(
                domain.id(),
                domain.orderId(),
                domain.status(),
                domain.latitude(),
                domain.longitude(),
                domain.recordedAt(),
                domain.recordedAt() != null ? domain.recordedAt() : Instant.now()
        );
    }
}
