package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeviceRegistry;
import com.builddash.backend.application.service.RefreshTokenRotator;
import com.builddash.backend.common.Sha256;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.Device;
import com.builddash.backend.domain.port.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceServiceImpl implements DeviceRegistry, RefreshTokenRotator {

    private final DeviceRepository deviceRepository;

    public DeviceServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional
    public Device create(UUID deviceId, UUID userId, String refreshTokenPlain, String deviceFingerprint) {
        Device device = new Device();
        device.setId(deviceId);
        device.setUserId(userId);
        device.setRefreshTokenHash(Sha256.hex(refreshTokenPlain));
        device.setDeviceFingerprint(deviceFingerprint);
        return deviceRepository.save(device);
    }

    @Override
    public List<Device> listActive(UUID userId) {
        return deviceRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new NotFoundException("DEVICE_NOT_FOUND", "Device not found"));
        device.setRevokedAt(Instant.now());
        deviceRepository.save(device);
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId) {
        deviceRepository.revokeAllActiveByUserId(userId, Instant.now());
    }

    @Override
    public String getFingerprintOrNull(UUID deviceId) {
        return deviceRepository.findById(deviceId).map(Device::getDeviceFingerprint).orElse(null);
    }

    /**
     * noRollbackFor is required here: Spring's default rollback-on-RuntimeException would
     * otherwise undo the revocation itself, since it's a side effect of the same exception path.
     * The revoke must be persisted explicitly — Device is a domain POJO returned by the
     * repository port, not a JPA-managed entity, so there is no dirty-checking auto-flush here.
     */
    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void validateForRefresh(UUID deviceId, UUID userId, String presentedRefreshToken) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .filter(d -> d.getRevokedAt() == null)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token is invalid or has been revoked"));

        if (!device.getRefreshTokenHash().equals(Sha256.hex(presentedRefreshToken))) {
            device.setRevokedAt(Instant.now());
            deviceRepository.save(device);
            throw new UnauthorizedException("REFRESH_TOKEN_REUSE_DETECTED", "Refresh token reuse detected, device has been revoked");
        }
    }

    @Override
    @Transactional
    public void rotateHash(UUID deviceId, String newRefreshTokenPlain) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("DEVICE_NOT_FOUND", "Device not found"));
        device.setRefreshTokenHash(Sha256.hex(newRefreshTokenPlain));
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
    }
}
