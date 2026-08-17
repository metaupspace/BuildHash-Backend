package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.GoogleUserInfo;

/**
 * Extracted so AuthService depends on an interface, not the concrete Google API client
 * (infra/external/GoogleTokenVerifier) directly — one implementation exists today, but the
 * gateway boundary is what lets application/domain code stay ignorant of the Google SDK.
 */
public interface GoogleIdentityGateway {

    GoogleUserInfo verify(String idTokenString);
}
