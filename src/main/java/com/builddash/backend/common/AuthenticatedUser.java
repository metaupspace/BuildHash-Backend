package com.builddash.backend.common;

import com.builddash.backend.domain.model.B2bMembership;

import java.util.List;
import java.util.UUID;

/**
 * b2bMemberships mirrors the token's "b2b" claim: empty for every B2C/guest/legacy
 * principal. These are ordinary-check credentials only — money-path operations re-check
 * membership in the database (decision 4), so a stale claim can never authorize them.
 * B2B roles are deliberately NOT Spring authorities.
 */
public record AuthenticatedUser(UUID userId, UUID deviceId, List<String> roles, List<B2bMembership> b2bMemberships) {

    public AuthenticatedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        b2bMemberships = b2bMemberships == null ? List.of() : List.copyOf(b2bMemberships);
    }

    /** Compatibility constructor preserving the pre-9A principal shape. */
    public AuthenticatedUser(UUID userId, UUID deviceId, List<String> roles) {
        this(userId, deviceId, roles, List.of());
    }

    /** Ordinary-check membership lookup: the caller's role in a company, or null if not a member. */
    public B2bMembership b2bMembership(UUID companyId) {
        return b2bMemberships.stream()
                .filter(m -> m.companyId().equals(companyId))
                .findFirst()
                .orElse(null);
    }
}
