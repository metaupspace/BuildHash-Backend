package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.NotifyMeSubscription;
import com.builddash.backend.domain.port.NotifyMeSubscriptionRepository;
import com.builddash.backend.infra.persistence.mapper.NotifyMeSubscriptionMapper;
import com.builddash.backend.infra.persistence.repository.NotifyMeSubscriptionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class NotifyMeSubscriptionRepositoryAdapter implements NotifyMeSubscriptionRepository {

    private final NotifyMeSubscriptionJpaRepository jpaRepository;

    NotifyMeSubscriptionRepositoryAdapter(NotifyMeSubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public NotifyMeSubscription save(NotifyMeSubscription subscription) {
        return NotifyMeSubscriptionMapper.toDomain(jpaRepository.save(NotifyMeSubscriptionMapper.toEntity(subscription)));
    }

    @Override
    public Optional<NotifyMeSubscription> findByProductIdAndUserId(UUID productId, UUID userId) {
        return jpaRepository.findByProductIdAndUserId(productId, userId).map(NotifyMeSubscriptionMapper::toDomain);
    }
}
