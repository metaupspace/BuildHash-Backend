package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.infra.persistence.entity.CatalogOutboxEventEntity;

public final class CatalogOutboxEventMapper {

    private CatalogOutboxEventMapper() {
    }

    public static CatalogOutboxEvent toDomain(CatalogOutboxEventEntity entity) {
        if (entity == null) {
            return null;
        }
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(entity.getId());
        event.setProductId(entity.getProductId());
        event.setEventType(entity.getEventType());
        event.setPayload(entity.getPayload());
        event.setStatus(entity.getStatus());
        event.setAttemptCount(entity.getAttemptCount());
        event.setLastAttemptAt(entity.getLastAttemptAt());
        event.setErrorMessage(entity.getErrorMessage());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }

    public static CatalogOutboxEventEntity toEntity(CatalogOutboxEvent event) {
        if (event == null) {
            return null;
        }
        CatalogOutboxEventEntity entity = new CatalogOutboxEventEntity();
        entity.setId(event.getId());
        entity.setProductId(event.getProductId());
        entity.setEventType(event.getEventType());
        entity.setPayload(event.getPayload());
        entity.setStatus(event.getStatus());
        entity.setAttemptCount(event.getAttemptCount());
        entity.setLastAttemptAt(event.getLastAttemptAt());
        entity.setErrorMessage(event.getErrorMessage());
        entity.setCreatedAt(event.getCreatedAt());
        return entity;
    }
}
