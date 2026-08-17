package com.builddash.backend.application.service;

import java.util.UUID;

/**
 * ISP: only the /auth/refresh flow needs this surface — distinct from the session-bookkeeping
 * concerns in DeviceRegistry (SRP: this is specifically the refresh-token security check).
 */
public interface RefreshTokenRotator {

    /**
     * Throws UnauthorizedException if the device is missing/revoked or the presented token
     * doesn't match the stored hash. A hash mismatch on an otherwise-valid device is treated as
     * reuse (compromise) and revokes the device as a side effect.
     */
    void validateForRefresh(UUID deviceId, UUID userId, String presentedRefreshToken);

    void rotateHash(UUID deviceId, String newRefreshTokenPlain);
}
