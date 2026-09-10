package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.NotifyMeSubscription;

import java.util.UUID;

public interface NotifyMeSubscriptionService {

    NotifyMeSubscription subscribe(UUID productId, UUID userId);
}
