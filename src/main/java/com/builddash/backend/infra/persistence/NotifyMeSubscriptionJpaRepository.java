package com.builddash.backend.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface NotifyMeSubscriptionJpaRepository extends JpaRepository<NotifyMeSubscriptionEntity, UUID> {

    Optional<NotifyMeSubscriptionEntity> findByProductIdAndUserId(UUID productId, UUID userId);
}
