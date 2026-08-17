package com.builddash.backend.infra.security;

import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.infra.config.JwtProperties;
import com.builddash.backend.domain.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Package-private signing/parsing engine shared by JwtTokenIssuer and JwtTokenValidator so the
 * HS256 key material and JJWT calls live in exactly one place. Not exposed outside this package —
 * callers depend on TokenIssuer/TokenValidator instead (ISP: each sees only the half it needs).
 */
@Component
class JwtCodec {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    JwtCodec(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    IssuedToken encode(UUID subject, long ttlSeconds, Map<String, Object> claims) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject.toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claims(claims)
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, ttlSeconds);
    }

    /**
     * Verifies signature and expiry only — callers are responsible for checking the {@code typ}
     * claim against what they expect (JwtCodec has no notion of token "kinds").
     */
    Claims decode(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "Token is invalid or expired");
        }
    }
}
