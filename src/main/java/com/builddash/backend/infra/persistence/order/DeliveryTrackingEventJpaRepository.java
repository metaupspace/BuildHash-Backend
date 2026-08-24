package com.builddash.backend.infra.persistence.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryTrackingEventJpaRepository extends JpaRepository<DeliveryTrackingEventEntity, UUID> {
    Optional<DeliveryTrackingEventEntity> findFirstByOrderIdOrderByRecordedAtDescIdDesc(UUID orderId);
}
