package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.LoginEvent;
import com.builddash.backend.infra.persistence.entity.LoginEventEntity;

public final class LoginEventMapper {

    private LoginEventMapper() {
    }

    public static LoginEvent toDomain(LoginEventEntity entity) {
        LoginEvent event = new LoginEvent();
        event.setId(entity.getId());
        event.setUserId(entity.getUserId());
        event.setEventType(entity.getEventType());
        event.setIpAddress(entity.getIpAddress());
        event.setDeviceFingerprint(entity.getDeviceFingerprint());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }

    public static LoginEventEntity toEntity(LoginEvent event) {
        LoginEventEntity entity = new LoginEventEntity();
        entity.setId(event.getId());
        entity.setUserId(event.getUserId());
        entity.setEventType(event.getEventType());
        entity.setIpAddress(event.getIpAddress());
        entity.setDeviceFingerprint(event.getDeviceFingerprint());
        entity.setCreatedAt(event.getCreatedAt());
        return entity;
    }
}
