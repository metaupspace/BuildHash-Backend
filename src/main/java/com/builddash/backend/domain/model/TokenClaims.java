package com.builddash.backend.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * deviceId is null when the validated token carries no device claim (guest tokens) —
 * callers never need to branch on token type to know whether it's safe to read.
 *
 * b2bMemberships is the parsed "b2b" claim: empty for every B2C/legacy/guest token and
 * for any malformed claim (fail-safe parsing in JwtTokenValidator — malformed content
 * degrades to fewer/empty memberships, never an auth failure). B2B roles are NOT part
 * of roles/authorities (decision 4).
 */
public record TokenClaims(UUID userId, UUID deviceId, List<String> roles, List<B2bMembership> b2bMemberships) {

    public TokenClaims {
        roles = roles == null ? List.of() : List.copyOf(roles);
        b2bMemberships = b2bMemberships == null ? List.of() : List.copyOf(b2bMemberships);
    }

    /** Compatibility constructor for callers that only carry application roles. */
    public TokenClaims(UUID userId, UUID deviceId, List<String> roles) {
        this(userId, deviceId, roles, List.of());
    }
}
