package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.NotifyMeSubscription;
import com.builddash.backend.infra.persistence.entity.NotifyMeSubscriptionEntity;
import com.builddash.backend.infra.persistence.mapper.NotifyMeSubscriptionMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyMeSubscriptionMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        NotifyMeSubscription original = new NotifyMeSubscription();
        original.setId(UUID.randomUUID());
        original.setProductId(UUID.randomUUID());
        original.setUserId(UUID.randomUUID());
        original.setCreatedAt(Instant.now());

        NotifyMeSubscriptionEntity entity = NotifyMeSubscriptionMapper.toEntity(original);
        NotifyMeSubscription roundTripped = NotifyMeSubscriptionMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getProductId()).isEqualTo(original.getProductId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
