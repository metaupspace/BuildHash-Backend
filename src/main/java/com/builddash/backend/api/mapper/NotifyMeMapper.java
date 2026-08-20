package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.NotifyMeSubscriptionResponse;
import com.builddash.backend.domain.model.NotifyMeSubscription;
import org.springframework.stereotype.Component;

@Component
public class NotifyMeMapper {

    public NotifyMeSubscriptionResponse toResponse(NotifyMeSubscription subscription) {
        return new NotifyMeSubscriptionResponse(subscription.getProductId(), subscription.getCreatedAt());
    }
}
