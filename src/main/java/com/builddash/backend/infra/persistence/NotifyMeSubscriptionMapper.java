package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.NotifyMeSubscription;

final class NotifyMeSubscriptionMapper {

    private NotifyMeSubscriptionMapper() {
    }

    static NotifyMeSubscription toDomain(NotifyMeSubscriptionEntity entity) {
        NotifyMeSubscription subscription = new NotifyMeSubscription();
        subscription.setId(entity.getId());
        subscription.setProductId(entity.getProductId());
        subscription.setUserId(entity.getUserId());
        subscription.setCreatedAt(entity.getCreatedAt());
        return subscription;
    }

    static NotifyMeSubscriptionEntity toEntity(NotifyMeSubscription subscription) {
        NotifyMeSubscriptionEntity entity = new NotifyMeSubscriptionEntity();
        entity.setId(subscription.getId());
        entity.setProductId(subscription.getProductId());
        entity.setUserId(subscription.getUserId());
        entity.setCreatedAt(subscription.getCreatedAt());
        return entity;
    }
}
