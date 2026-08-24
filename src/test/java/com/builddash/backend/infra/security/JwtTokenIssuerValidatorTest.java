package com.builddash.backend.infra.security;

import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.infra.config.JwtProperties;
import com.builddash.backend.domain.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests both halves of the split together since they share one JwtCodec instance and the
 * interesting behavior (round-tripping, type-checking, expiry) only shows up crossing the pair.
 */
class JwtTokenIssuerValidatorTest {

    private static final String SECRET = "unit-test-secret-key-must-be-long-enough-0123456789";

    private JwtCodec codec;
    private JwtTokenIssuer issuer;
    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("builddash-backend-test");
        properties.setAccessTokenTtlMinutes(15);
        properties.setRefreshTokenTtlDays(30);
        properties.setGuestTokenTtlHours(24);

        codec = new JwtCodec(properties);
        issuer = new JwtTokenIssuer(codec, properties);
        validator = new JwtTokenValidator(codec);
    }

    @Test
    void issuesAndParsesAccessToken() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        IssuedToken token = issuer.issueAccessToken(userId, deviceId, List.of("USER"));
        TokenClaims claims = validator.validate(token.token(), TokenType.ACCESS);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.deviceId()).isEqualTo(deviceId);
        assertThat(claims.roles()).containsExactly("USER");
        assertThat(token.expiresInSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void issuesAndParsesRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        IssuedToken token = issuer.issueRefreshToken(userId, deviceId);
        TokenClaims claims = validator.validate(token.token(), TokenType.REFRESH);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.deviceId()).isEqualTo(deviceId);
    }

    @Test
    void issuesGuestTokenWithGuestRoleAndNoDeviceId() {
        IssuedToken token = issuer.issueGuestToken(java.util.UUID.randomUUID());
        TokenClaims claims = validator.validate(token.token(), TokenType.GUEST);

        assertThat(claims.roles()).containsExactly("GUEST");
        assertThat(claims.deviceId()).isNull();
    }

    @Test
    void issuingTwoTokensForSameSubjectAndDeviceProducesDistinctTokens() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        String first = issuer.issueRefreshToken(userId, deviceId).token();
        String second = issuer.issueRefreshToken(userId, deviceId).token();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsTokenPresentedAsWrongType() {
        IssuedToken accessToken = issuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("USER"));

        assertThatThrownBy(() -> validator.validate(accessToken.token(), TokenType.REFRESH))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String expiredToken = codec.encode(UUID.randomUUID(), -60, Map.of("typ", "ACCESS", "roles", List.of("USER"),
                "deviceId", UUID.randomUUID().toString())).token();

        assertThatThrownBy(() -> validator.validate(expiredToken, TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTamperedSignature() {
        IssuedToken token = issuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("USER"));
        String tampered = token.token().substring(0, token.token().length() - 1) + "x";

        assertThatThrownBy(() -> validator.validate(tampered, TokenType.ACCESS))
                .isInstanceOf(UnauthorizedException.class);
    }
}
