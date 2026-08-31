package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.B2bMembership;

import java.util.List;
import java.util.UUID;

/**
 * Read-side membership projection for token issuance: resolves a user's complete B2B
 * context (one entry per company, with site scope). Called at login/refresh only, so
 * the per-request path stays free of membership DB reads (decision 4).
 */
public interface CompanyMembershipResolver {

    List<B2bMembership> resolveByUserId(UUID userId);
}
