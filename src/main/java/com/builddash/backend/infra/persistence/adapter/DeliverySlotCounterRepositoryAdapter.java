package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.infra.persistence.entity.DeliverySlotCounterEntity;
import com.builddash.backend.infra.persistence.mapper.DeliverySlotMapper;
import com.builddash.backend.infra.persistence.repository.DeliverySlotCounterJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class DeliverySlotCounterRepositoryAdapter implements DeliverySlotCounterRepository {

    private final DeliverySlotCounterJpaRepository jpaRepository;

    @Override
    public Optional<DeliverySlotCounter> findBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate) {
        return jpaRepository.findBySlotIdAndSlotDate(slotId, slotDate)
                .map(DeliverySlotMapper::toDomain);
    }

    @Override
    public Optional<DeliverySlotCounter> findBySlotIdAndSlotDateForUpdate(UUID slotId, LocalDate slotDate) {
        return jpaRepository.findBySlotIdAndSlotDateForUpdate(slotId, slotDate)
                .map(DeliverySlotMapper::toDomain);
    }

    @Override
    public List<DeliverySlotCounter> findBySlotDate(LocalDate slotDate) {
        return jpaRepository.findBySlotDate(slotDate).stream()
                .map(DeliverySlotMapper::toDomain)
                .toList();
    }

    @Override
    public DeliverySlotCounter save(DeliverySlotCounter counter) {
        DeliverySlotCounterEntity saved = jpaRepository.save(DeliverySlotMapper.toEntity(counter));
        return DeliverySlotMapper.toDomain(saved);
    }

    @Override
    public boolean existsBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate) {
        return jpaRepository.existsBySlotIdAndSlotDate(slotId, slotDate);
    }

    @Override
    @Transactional
    public void insertIfNotExists(UUID id, UUID slotId, LocalDate slotDate, int capacity) {
        jpaRepository.insertIfNotExists(id, slotId, slotDate, capacity);
    }
}
