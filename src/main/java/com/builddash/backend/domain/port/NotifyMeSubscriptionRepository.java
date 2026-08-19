package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.NotifyMeSubscription;

import java.util.Optional;
import java.util.UUID;

public interface NotifyMeSubscriptionRepository {

    NotifyMeSubscription save(NotifyMeSubscription subscription);

    Optional<NotifyMeSubscription> findByProductIdAndUserId(UUID productId, UUID userId);
}
