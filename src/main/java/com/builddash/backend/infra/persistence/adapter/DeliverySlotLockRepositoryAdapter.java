package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.port.DeliverySlotLockRepository;
import com.builddash.backend.infra.persistence.entity.DeliverySlotLockEntity;
import com.builddash.backend.infra.persistence.mapper.DeliverySlotMapper;
import com.builddash.backend.infra.persistence.repository.DeliverySlotLockJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class DeliverySlotLockRepositoryAdapter implements DeliverySlotLockRepository {

    private final DeliverySlotLockJpaRepository jpaRepository;

    @Override
    public DeliverySlotLock save(DeliverySlotLock lock) {
        DeliverySlotLockEntity saved = jpaRepository.save(DeliverySlotMapper.toEntity(lock));
        return DeliverySlotMapper.toDomain(saved);
    }

    @Override
    public Optional<DeliverySlotLock> findActiveByUserId(UUID userId, Instant asOf) {
        return jpaRepository.findActiveByUserId(userId, asOf)
                .map(DeliverySlotMapper::toDomain);
    }

    @Override
    public Optional<DeliverySlotLock> findById(UUID lockId) {
        return jpaRepository.findById(lockId)
                .map(DeliverySlotMapper::toDomain);
    }

    @Override
    public List<DeliverySlotLock> findExpiredActiveLocks(Instant asOf) {
        return jpaRepository.findExpiredActiveLocks(asOf).stream()
                .map(DeliverySlotMapper::toDomain)
                .toList();
    }

    @Override
    public void updateStatus(UUID lockId, DeliverySlotLockStatus status) {
        jpaRepository.updateStatus(lockId, status);
    }
}
