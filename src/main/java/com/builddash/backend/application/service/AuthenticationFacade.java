package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.AuthSession;
import com.builddash.backend.domain.model.OtpSendResult;

import java.util.UUID;

/**
 * DIP: AuthController and DeviceController depend on this interface, never on AuthServiceImpl
 * directly — the concrete orchestration wiring is swappable without touching either controller.
 * Takes/returns domain types only — api/mapper builds the request/response DTOs at the boundary.
 */
public interface AuthenticationFacade {

    OtpSendResult sendOtp(String phone);

    AuthSession verifyOtp(String phone, String otp, String deviceFingerprint, String ip, String guestToken);

    AuthSession googleSignIn(String idToken, String deviceFingerprint, String ip, String guestToken);

    AuthSession guestSession();

    AuthSession refresh(String refreshToken);

    AuthSession logoutAllDevicesAndReissue(UUID userId, UUID currentDeviceId);
}
