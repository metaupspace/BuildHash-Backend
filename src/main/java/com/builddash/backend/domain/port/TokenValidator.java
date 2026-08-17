package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.model.TokenClaims;

/**
 * ISP: callers that only need to check an incoming token (JwtAuthenticationFilter, refresh flow)
 * never see issuance methods.
 */
public interface TokenValidator {

    /**
     * Verifies signature/expiry, then asserts the {@code typ} claim matches expectedType so a
     * leaked refresh token can't be replayed as an access token (or vice versa). Throws
     * UnauthorizedException on any failure — never a lower-level parsing exception, so every
     * implementation is substitutable without callers needing implementation-specific catches (LSP).
     */
    TokenClaims validate(String token, TokenType expectedType);
}
