package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @Override
    public Optional<UUID> findOrderId(String key) {
        return jpaRepository.findById(key).map(IdempotencyKeyEntity::getOrderId);
    }

    @Override
    public void save(String key, UUID orderId) {
        jpaRepository.save(new IdempotencyKeyEntity(key, orderId));
    }
}
