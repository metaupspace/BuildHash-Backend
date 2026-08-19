package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.CatalogOutboxEvent;

final class CatalogOutboxEventMapper {

    private CatalogOutboxEventMapper() {
    }

    static CatalogOutboxEvent toDomain(CatalogOutboxEventEntity entity) {
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(entity.getId());
        event.setProductId(entity.getProductId());
        event.setEventType(entity.getEventType());
        event.setPayload(entity.getPayload());
        event.setStatus(entity.getStatus());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }

    static CatalogOutboxEventEntity toEntity(CatalogOutboxEvent event) {
        CatalogOutboxEventEntity entity = new CatalogOutboxEventEntity();
        entity.setId(event.getId());
        entity.setProductId(event.getProductId());
        entity.setEventType(event.getEventType());
        entity.setPayload(event.getPayload());
        entity.setStatus(event.getStatus());
        entity.setCreatedAt(event.getCreatedAt());
        return entity;
    }
}
