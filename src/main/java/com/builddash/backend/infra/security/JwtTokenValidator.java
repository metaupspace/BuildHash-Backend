package com.builddash.backend.infra.security;

import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.port.TokenValidator;
import com.builddash.backend.domain.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenValidator implements TokenValidator {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_DEVICE_ID = "deviceId";

    private final JwtCodec codec;

    public JwtTokenValidator(JwtCodec codec) {
        this.codec = codec;
    }

    @Override
    public TokenClaims validate(String token, TokenType expectedType) {
        Claims claims = codec.decode(token);
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.name().equals(type)) {
            throw new UnauthorizedException("INVALID_TOKEN_TYPE", "Expected a " + expectedType.name() + " token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String deviceIdClaim = claims.get(CLAIM_DEVICE_ID, String.class);
        UUID deviceId = deviceIdClaim == null ? null : UUID.fromString(deviceIdClaim);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get(CLAIM_ROLES, List.class);

        return new TokenClaims(userId, deviceId, roles);
    }
}
