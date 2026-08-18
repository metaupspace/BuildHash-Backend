package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Device;
import com.builddash.backend.infra.persistence.entity.DeviceEntity;

public final class DeviceMapper {

    private DeviceMapper() {
    }

    public static Device toDomain(DeviceEntity entity) {
        Device device = new Device();
        device.setId(entity.getId());
        device.setUserId(entity.getUserId());
        device.setRefreshTokenHash(entity.getRefreshTokenHash());
        device.setDeviceFingerprint(entity.getDeviceFingerprint());
        device.setLastSeenAt(entity.getLastSeenAt());
        device.setCreatedAt(entity.getCreatedAt());
        device.setRevokedAt(entity.getRevokedAt());
        return device;
    }

    public static DeviceEntity toEntity(Device device) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(device.getId());
        entity.setUserId(device.getUserId());
        entity.setRefreshTokenHash(device.getRefreshTokenHash());
        entity.setDeviceFingerprint(device.getDeviceFingerprint());
        entity.setLastSeenAt(device.getLastSeenAt());
        entity.setCreatedAt(device.getCreatedAt());
        entity.setRevokedAt(device.getRevokedAt());
        return entity;
    }
}
