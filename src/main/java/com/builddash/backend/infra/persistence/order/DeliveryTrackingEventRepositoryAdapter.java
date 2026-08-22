package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryTrackingEventRepositoryAdapter implements DeliveryTrackingEventRepository {

    private final DeliveryTrackingEventJpaRepository jpaRepository;

    @Override
    public DeliveryTrackingEvent save(DeliveryTrackingEvent event) {
        DeliveryTrackingEventEntity entity = new DeliveryTrackingEventEntity();
        entity.setId(event.getId());
        entity.setOrderId(event.getOrderId());
        entity.setStatus(event.getStatus());
        entity.setLatitude(event.getLatitude());
        entity.setLongitude(event.getLongitude());
        entity.setRecordedAt(event.getRecordedAt());

        DeliveryTrackingEventEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<DeliveryTrackingEvent> findLatestByOrderId(UUID orderId) {
        return jpaRepository.findFirstByOrderIdOrderByRecordedAtDescIdDesc(orderId).map(this::toDomain);
    }

    private DeliveryTrackingEvent toDomain(DeliveryTrackingEventEntity entity) {
        DeliveryTrackingEvent event = new DeliveryTrackingEvent(
                entity.getId(),
                entity.getOrderId(),
                entity.getStatus(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getRecordedAt()
        );
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }
}
