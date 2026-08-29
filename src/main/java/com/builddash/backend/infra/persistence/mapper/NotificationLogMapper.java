package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.infra.persistence.entity.NotificationLogEntity;

public final class NotificationLogMapper {

    private NotificationLogMapper() {
    }

    public static NotificationLog toDomain(NotificationLogEntity entity) {
        NotificationLog log = new NotificationLog();
        log.setId(entity.getId());
        log.setUserId(entity.getUserId());
        log.setRecipientPhone(entity.getRecipientPhone());
        log.setChannel(entity.getChannel());
        log.setEventType(entity.getEventType());
        log.setReferenceId(entity.getReferenceId());
        log.setStatus(entity.getStatus());
        log.setSentAt(entity.getSentAt());
        log.setDeliveredAt(entity.getDeliveredAt());
        log.setCreatedAt(entity.getCreatedAt());
        log.setUpdatedAt(entity.getUpdatedAt());
        return log;
    }

    public static NotificationLogEntity toEntity(NotificationLog log) {
        NotificationLogEntity entity = new NotificationLogEntity();
        entity.setId(log.getId());
        entity.setUserId(log.getUserId());
        entity.setRecipientPhone(log.getRecipientPhone());
        entity.setChannel(log.getChannel());
        entity.setEventType(log.getEventType());
        entity.setReferenceId(log.getReferenceId());
        entity.setStatus(log.getStatus());
        entity.setSentAt(log.getSentAt());
        entity.setDeliveredAt(log.getDeliveredAt());
        entity.setCreatedAt(log.getCreatedAt());
        entity.setUpdatedAt(log.getUpdatedAt());
        return entity;
    }
}
