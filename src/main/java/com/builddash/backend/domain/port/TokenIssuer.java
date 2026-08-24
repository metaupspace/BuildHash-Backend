package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.IssuedToken;

import java.util.List;
import java.util.UUID;

/**
 * ISP: callers that only need to mint tokens (AuthService) never see parsing/validation methods.
 */
public interface TokenIssuer {

    IssuedToken issueAccessToken(UUID userId, UUID deviceId, List<String> roles);

    IssuedToken issueRefreshToken(UUID userId, UUID deviceId);

    IssuedToken issueGuestToken(java.util.UUID userId);
}
