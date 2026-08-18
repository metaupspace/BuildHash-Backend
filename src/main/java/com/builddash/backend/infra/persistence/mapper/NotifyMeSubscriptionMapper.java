package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.NotifyMeSubscription;
import com.builddash.backend.infra.persistence.entity.NotifyMeSubscriptionEntity;

public final class NotifyMeSubscriptionMapper {

    private NotifyMeSubscriptionMapper() {
    }

    public static NotifyMeSubscription toDomain(NotifyMeSubscriptionEntity entity) {
        NotifyMeSubscription subscription = new NotifyMeSubscription();
        subscription.setId(entity.getId());
        subscription.setProductId(entity.getProductId());
        subscription.setUserId(entity.getUserId());
        subscription.setCreatedAt(entity.getCreatedAt());
        return subscription;
    }

    public static NotifyMeSubscriptionEntity toEntity(NotifyMeSubscription subscription) {
        NotifyMeSubscriptionEntity entity = new NotifyMeSubscriptionEntity();
        entity.setId(subscription.getId());
        entity.setProductId(subscription.getProductId());
        entity.setUserId(subscription.getUserId());
        entity.setCreatedAt(subscription.getCreatedAt());
        return entity;
    }
}
