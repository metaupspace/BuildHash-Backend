package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.DeliveryTrackingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryTrackingEventJpaRepository extends JpaRepository<DeliveryTrackingEventEntity, UUID> {
    Optional<DeliveryTrackingEventEntity> findFirstByOrderIdOrderByRecordedAtDesc(UUID orderId);
    List<DeliveryTrackingEventEntity> findByOrderIdOrderByRecordedAtDesc(UUID orderId);
}
