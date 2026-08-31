package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyPermission;

import java.util.UUID;

/**
 * The single B2B authorization choke point. Every company-scoped operation routes
 * through authorize() — controllers never see permissions, services never scatter
 * permission strings.
 *
 * critical=true marks a money/security-sensitive mutation: it takes the company-row
 * lock FIRST (the same serialization point the membership/permission mutations use),
 * so a concurrent permission revoke or role change either commits before this read
 * (mutation sees the new state and stops) or waits behind it. No interleaving window.
 *
 * Resolution is always against current database state — the JWT carries membership
 * context only (companyId/role/siteIds), never permissions, so revocation is
 * effective on the very next request without token refresh.
 */
public interface B2bAuthorizer {

    /**
     * @param resourceSiteId site-scoped resource's site, or null for company-wide
     *                       operations (permission + membership alone decide).
     * @param critical       true for mutations: company-row lock + 403 on site
     *                       mismatch; false for reads: 404 on site mismatch.
     * @throws NotFoundException    (404) not a member of the company
     * @throws ForbiddenException   (403) permission not held by the member's role
     * @throws NotFoundException    (404) read of a site out of the member's scope
     * @throws ForbiddenException   (403) mutation on a site out of scope
     */
    void authorize(UUID userId, UUID companyId, CompanyPermission requiredPermission,
                   UUID resourceSiteId, boolean critical);
}
