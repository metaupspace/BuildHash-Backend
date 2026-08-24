package com.builddash.backend.infra.security;

import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.infra.config.JwtProperties;
import com.builddash.backend.domain.port.TokenIssuer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_SESSION_ID = "sessionId";

    private final JwtCodec codec;
    private final JwtProperties properties;


    @Override
    public IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles) {
        long ttlSeconds = properties.getAccessTokenTtlMinutes() * 60;
        return codec.encode(userId, ttlSeconds, Map.of(
                CLAIM_TYPE, TokenType.ACCESS.name(),
                CLAIM_ROLES, roles,
                CLAIM_DEVICE_ID, deviceId.toString()));
    }

    @Override
    public IssuedToken issueRefreshToken(UUID userId, UUID deviceId) {
        long ttlSeconds = properties.getRefreshTokenTtlDays() * 24 * 60 * 60;
        return codec.encode(userId, ttlSeconds, Map.of(
                CLAIM_TYPE, TokenType.REFRESH.name(),
                CLAIM_DEVICE_ID, deviceId.toString()));
    }

    @Override
    public IssuedToken issueGuestToken(UUID userId) {
        long ttlSeconds = properties.getGuestTokenTtlHours() * 60 * 60;
        return codec.encode(userId, ttlSeconds, Map.of(
                CLAIM_TYPE, TokenType.GUEST.name(),
                CLAIM_ROLES, List.of("GUEST"),
                CLAIM_SESSION_ID, UUID.randomUUID().toString()));
    }
}
