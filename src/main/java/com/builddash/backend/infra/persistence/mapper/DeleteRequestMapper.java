package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.DeleteRequest;
import com.builddash.backend.infra.persistence.entity.DeleteRequestEntity;

public final class DeleteRequestMapper {

    private DeleteRequestMapper() {
    }

    public static DeleteRequestEntity toEntity(DeleteRequest request) {
        DeleteRequestEntity entity = new DeleteRequestEntity();
        entity.setId(request.id());
        entity.setUserId(request.userId());
        entity.setRequestedAt(request.requestedAt());
        entity.setDeletionScheduledAt(request.deletionScheduledAt());
        entity.setProcessedAt(request.processedAt());
        entity.setStatus(request.status());
        return entity;
    }

    public static DeleteRequest toDomain(DeleteRequestEntity entity) {
        return new DeleteRequest(entity.getId(), entity.getUserId(), entity.getRequestedAt(),
                entity.getDeletionScheduledAt(), entity.getProcessedAt(), entity.getStatus());
    }
}
