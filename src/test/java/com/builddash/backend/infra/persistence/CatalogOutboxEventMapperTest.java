package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.infra.persistence.entity.CatalogOutboxEventEntity;
import com.builddash.backend.infra.persistence.mapper.CatalogOutboxEventMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogOutboxEventMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        CatalogOutboxEvent original = new CatalogOutboxEvent();
        original.setId(UUID.randomUUID());
        original.setProductId(UUID.randomUUID());
        original.setEventType(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
        original.setPayload("{\"productId\":\"abc\"}");
        original.setStatus(OutboxStatus.PUBLISHED);
        original.setCreatedAt(Instant.now());

        CatalogOutboxEventEntity entity = CatalogOutboxEventMapper.toEntity(original);
        CatalogOutboxEvent roundTripped = CatalogOutboxEventMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getProductId()).isEqualTo(original.getProductId());
        assertThat(roundTripped.getEventType()).isEqualTo(original.getEventType());
        assertThat(roundTripped.getPayload()).isEqualTo(original.getPayload());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
