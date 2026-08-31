package com.builddash.backend.infra.security;

import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.infra.config.JwtProperties;
import com.builddash.backend.domain.port.TokenIssuer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
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
    /**
     * B2B membership context (decision 4). Serialized as [{cid, role, sites}] —
     * compact and self-describing; the "b2b" name stays stable forever because tokens
     * live past deployments. B2C tokens simply carry an empty list.
     */
    private static final String CLAIM_B2B = "b2b";

    private final JwtCodec codec;
    private final JwtProperties properties;


    @Override
    public IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles) {
        return issueAccessToken(userId, deviceId, roles, List.of());
    }

    @Override
    public IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles,
                                        List<B2bMembership> b2bMemberships) {
        long ttlSeconds = properties.getAccessTokenTtlMinutes() * 60;
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TokenType.ACCESS.name());
        claims.put(CLAIM_ROLES, roles);
        claims.put(CLAIM_DEVICE_ID, deviceId.toString());
        claims.put(CLAIM_B2B, toClaimValue(b2bMemberships));
        return codec.encode(userId, ttlSeconds, claims);
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

    private static List<Map<String, Object>> toClaimValue(List<B2bMembership> memberships) {
        List<Map<String, Object>> value = new ArrayList<>();
        for (B2bMembership membership : memberships) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("cid", membership.companyId().toString());
            entry.put("role", membership.role().name());
            entry.put("sites", membership.siteIds().stream().map(UUID::toString).toList());
            value.add(entry);
        }
        return value;
    }
}
