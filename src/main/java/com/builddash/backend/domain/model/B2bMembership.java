package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CompanyRole;

import java.util.List;
import java.util.UUID;

/**
 * Value carried in the JWT "b2b" claim and on AuthenticatedUser: one entry per company
 * membership. Empty siteIds means all sites (unscoped member); a non-empty list scopes
 * the member to those sites. Fail-safe parsing lives in JwtTokenValidator — malformed
 * claim content degrades to fewer/empty memberships, never to an auth failure or to
 * granted access.
 */
public record B2bMembership(
        UUID companyId,
        CompanyRole role,
        List<UUID> siteIds
) {

    public B2bMembership {
        siteIds = siteIds == null ? List.of() : List.copyOf(siteIds);
    }

    /** Site-scope check: unscoped members (empty sites) cover every site. */
    public boolean coversSite(UUID siteId) {
        return siteIds.isEmpty() || siteIds.contains(siteId);
    }
}
