package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.AuthenticationFacade;
import com.builddash.backend.application.service.DeviceRegistry;
import com.builddash.backend.application.service.LoginEventRecorder;
import com.builddash.backend.application.service.RefreshTokenRotator;
import com.builddash.backend.application.service.UserAccountService;
import com.builddash.backend.domain.enums.LoginEventType;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.model.AuthSession;
import com.builddash.backend.domain.model.GoogleUserInfo;
import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.domain.model.OtpSendResult;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.GoogleIdentityGateway;
import com.builddash.backend.domain.port.PhoneExistenceIndex;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.TokenValidator;
import com.builddash.backend.domain.port.OtpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * SRP: orchestrates the login/session workflows only — every actual piece of logic (OTP checks,
 * token crypto, device bookkeeping, user lookup, audit logging) is delegated to its own
 * single-purpose collaborator, injected here by interface (DIP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthenticationFacade {

    private final OtpSendService otpSendService;
    private final OtpVerificationService otpVerificationService;
    private final OtpConfig otpConfig;
    private final TokenIssuer tokenIssuer;
    private final TokenValidator tokenValidator;
    private final GoogleIdentityGateway googleIdentityGateway;
    private final UserAccountService userAccountService;
    private final DeviceRegistry deviceRegistry;
    private final RefreshTokenRotator refreshTokenRotator;
    private final CartService cartService;
    private final LoginEventRecorder loginEventRecorder;
    private final PhoneExistenceIndex phoneExistenceIndex;


    @Override
    public OtpSendResult sendOtp(String phone) {
        otpSendService.send(phone);
        return new OtpSendResult(otpConfig.getTtlSeconds(), phoneExistenceIndex.mightExist(phone));
    }

    @Override
    public AuthSession verifyOtp(String phone, String otp, String deviceFingerprint, String ip, String guestToken) {
        otpVerificationService.verify(phone, otp);
        User user = userAccountService.findOrCreateByPhone(phone);
        phoneExistenceIndex.markExists(phone);
        
        handleGuestMerge(guestToken, user.getId());
        
        loginEventRecorder.record(user.getId(), LoginEventType.OTP, ip, deviceFingerprint);
        return issueSession(user.getId(), deviceFingerprint);
    }

    @Override
    public AuthSession googleSignIn(String idToken, String deviceFingerprint, String ip, String guestToken) {
        GoogleUserInfo info = googleIdentityGateway.verify(idToken);
        User user = userAccountService.findOrCreateByGoogle(info.googleId(), info.email(), info.name());
        
        handleGuestMerge(guestToken, user.getId());
        
        loginEventRecorder.record(user.getId(), LoginEventType.GOOGLE, ip, deviceFingerprint);
        return issueSession(user.getId(), deviceFingerprint);
    }

    @Override
    public AuthSession guestSession() {
        User guest = userAccountService.createGuestUser();
        IssuedToken guestToken = tokenIssuer.issueGuestToken(guest.getId());
        return new AuthSession(guestToken.token(), null, "Bearer", guestToken.expiresInSeconds());
    }

    @Override
    public AuthSession refresh(String refreshToken) {
        TokenClaims claims = tokenValidator.validate(refreshToken, TokenType.REFRESH);
        UUID userId = claims.userId();
        UUID deviceId = claims.deviceId();

        refreshTokenRotator.validateForRefresh(deviceId, userId, refreshToken);

        IssuedToken access = tokenIssuer.issueAccessToken(userId, deviceId, List.of("USER"));
        IssuedToken newRefresh = tokenIssuer.issueRefreshToken(userId, deviceId);
        refreshTokenRotator.rotateHash(deviceId, newRefresh.token());

        return new AuthSession(access.token(), newRefresh.token(), "Bearer", access.expiresInSeconds());
    }

    @Override
    public AuthSession logoutAllDevicesAndReissue(UUID userId, UUID currentDeviceId) {
        String fingerprint = deviceRegistry.getFingerprintOrNull(currentDeviceId);
        deviceRegistry.revokeAll(userId);
        return issueSession(userId, fingerprint);
    }

    
    private void handleGuestMerge(String guestToken, UUID realUserId) {
        if (guestToken == null || guestToken.isBlank()) {
            return;
        }
        try {
            TokenClaims claims = tokenValidator.validate(guestToken, TokenType.GUEST);
            UUID guestUserId = claims.userId();
            cartService.mergeGuestCart(guestUserId, realUserId);
            userAccountService.markGuestMerged(guestUserId, realUserId);
        } catch (Exception e) {
            // Login must never fail because a guest cart could not be merged — but never silently
            log.warn("Guest cart merge failed for real user {}: {}", realUserId, e.getMessage(), e);
        }
    }

    private AuthSession issueSession(UUID userId, String deviceFingerprint) {
        UUID deviceId = UUID.randomUUID();
        IssuedToken access = tokenIssuer.issueAccessToken(userId, deviceId, List.of("USER"));
        IssuedToken refresh = tokenIssuer.issueRefreshToken(userId, deviceId);
        deviceRegistry.create(deviceId, userId, refresh.token(), deviceFingerprint);
        return new AuthSession(access.token(), refresh.token(), "Bearer", access.expiresInSeconds());
    }
}
