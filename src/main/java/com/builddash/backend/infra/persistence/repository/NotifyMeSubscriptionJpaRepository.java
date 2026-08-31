package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.NotifyMeSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotifyMeSubscriptionJpaRepository extends JpaRepository<NotifyMeSubscriptionEntity, UUID> {

    Optional<NotifyMeSubscriptionEntity> findByProductIdAndUserId(UUID productId, UUID userId);

    java.util.List<NotifyMeSubscriptionEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
