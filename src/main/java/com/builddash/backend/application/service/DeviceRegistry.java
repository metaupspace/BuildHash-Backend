package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Device;

import java.util.List;
import java.util.UUID;

/**
 * ISP: session-bookkeeping surface (DeviceController, AuthService issuing a new session) —
 * refresh-token cryptographic validation lives in RefreshTokenRotator instead. Returns the
 * domain model — api/mapper builds DeviceResponse in the controller.
 */
public interface DeviceRegistry {

    Device create(UUID deviceId, UUID userId, String refreshTokenPlain, String deviceFingerprint);

    List<Device> listActive(UUID userId);

    void revoke(UUID userId, UUID deviceId);

    void revokeAll(UUID userId);

    String getFingerprintOrNull(UUID deviceId);
}
