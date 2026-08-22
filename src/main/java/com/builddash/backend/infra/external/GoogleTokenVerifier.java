package com.builddash.backend.infra.external;

import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.GoogleUserInfo;
import com.builddash.backend.domain.port.GoogleIdentityGateway;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Always re-verifies the ID token server-side against Google's public keys on every call —
 * never cache a "trusted" result, since a client-asserted email/subject can't be trusted otherwise.
 */
@Component
public class GoogleTokenVerifier implements GoogleIdentityGateway {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IllegalArgumentException | java.io.IOException e) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID token could not be verified");
        }

        if (idToken == null) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google ID token is invalid or expired");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        return new GoogleUserInfo(payload.getSubject(), payload.getEmail(), (String) payload.get("name"));
    }
}
