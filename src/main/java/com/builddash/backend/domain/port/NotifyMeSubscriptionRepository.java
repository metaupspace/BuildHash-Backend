package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.NotifyMeSubscription;

import java.util.Optional;
import java.util.UUID;

public interface NotifyMeSubscriptionRepository {

    NotifyMeSubscription save(NotifyMeSubscription subscription);

    Optional<NotifyMeSubscription> findByProductIdAndUserId(UUID productId, UUID userId);

    /** DPDP export: all of the user's back-in-stock subscriptions. */
    java.util.List<NotifyMeSubscription> findAllByUserId(UUID userId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)). */
    void deleteByUserId(UUID userId);
}
