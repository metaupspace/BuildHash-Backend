package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @Override
    public Optional<UUID> findOrderId(String key, Instant createdAfter) {
        return jpaRepository.findById(key)
                .filter(entity -> entity.getCreatedAt() != null && entity.getCreatedAt().isAfter(createdAfter))
                .map(IdempotencyKeyEntity::getOrderId);
    }

    @Override
    public void save(String key, UUID orderId) {
        jpaRepository.save(new IdempotencyKeyEntity(key, orderId));
    }

    @Override
    @Transactional
    public int deleteCreatedBefore(Instant cutoff) {
        return jpaRepository.deleteByCreatedAtBefore(cutoff);
    }
}
