package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.LoginEvent;

final class LoginEventMapper {

    private LoginEventMapper() {
    }

    static LoginEvent toDomain(LoginEventEntity entity) {
        LoginEvent event = new LoginEvent();
        event.setId(entity.getId());
        event.setUserId(entity.getUserId());
        event.setEventType(entity.getEventType());
        event.setIpAddress(entity.getIpAddress());
        event.setDeviceFingerprint(entity.getDeviceFingerprint());
        event.setCreatedAt(entity.getCreatedAt());
        return event;
    }

    static LoginEventEntity toEntity(LoginEvent event) {
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
