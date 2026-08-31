package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.IssuedToken;

import java.util.List;
import java.util.UUID;

/**
 * ISP: callers that only need to mint tokens (AuthService) never see parsing/validation methods.
 */
public interface TokenIssuer {

    /**
     * Pre-9A shape, preserved for every caller that mints application-role tokens
     * (tests, guest-adjacent flows): delegates with no B2B context.
     */
    IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles);

    /** Full issuance: application roles plus the B2B membership context claim (decision 4). */
    IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles, List<B2bMembership> b2bMemberships);

    IssuedToken issueRefreshToken(UUID userId, UUID deviceId);

    IssuedToken issueGuestToken(java.util.UUID userId);
}
