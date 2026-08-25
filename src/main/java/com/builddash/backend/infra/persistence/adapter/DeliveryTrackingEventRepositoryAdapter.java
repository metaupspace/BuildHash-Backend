package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
import com.builddash.backend.infra.persistence.entity.DeliveryTrackingEventEntity;
import com.builddash.backend.infra.persistence.mapper.DeliveryTrackingEventMapper;
import com.builddash.backend.infra.persistence.repository.DeliveryTrackingEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeliveryTrackingEventRepositoryAdapter implements DeliveryTrackingEventRepository {

    private final DeliveryTrackingEventJpaRepository jpaRepository;

    @Override
    public DeliveryTrackingEvent save(DeliveryTrackingEvent event) {
        DeliveryTrackingEventEntity saved = jpaRepository.save(DeliveryTrackingEventMapper.toEntity(event));
        return DeliveryTrackingEventMapper.toDomain(saved);
    }

    @Override
    public Optional<DeliveryTrackingEvent> findLatestByOrderId(UUID orderId) {
        return jpaRepository.findFirstByOrderIdOrderByRecordedAtDesc(orderId)
                .map(DeliveryTrackingEventMapper::toDomain);
    }

    @Override
    public List<DeliveryTrackingEvent> findAllByOrderId(UUID orderId) {
        return jpaRepository.findByOrderIdOrderByRecordedAtDesc(orderId).stream()
                .map(DeliveryTrackingEventMapper::toDomain)
                .toList();
    }
}
