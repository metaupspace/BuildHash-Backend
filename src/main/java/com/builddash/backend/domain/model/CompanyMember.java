package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CompanyRole;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's membership in one company. Multi-company membership is permitted (OQ-1):
 * uniqueness is (companyId, userId) per company, never global. The last OWNER/ADMIN
 * invariant is enforced by CompanyMembershipServiceImpl under pessimistic row locks —
 * the DB UNIQUE constraint covers duplicates, the invariant covers stranded companies.
 */
public record CompanyMember(
        UUID id,
        UUID companyId,
        UUID userId,
        CompanyRole role,
        Instant createdAt,
        Instant updatedAt
) {

    public CompanyMember withRole(CompanyRole newRole) {
        return new CompanyMember(id, companyId, userId, newRole, createdAt, updatedAt);
    }
}
