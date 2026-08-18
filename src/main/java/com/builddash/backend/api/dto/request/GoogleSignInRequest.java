package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleSignInRequest(
        @NotBlank
        @Schema(example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...", description = "Google-issued ID token from the client SDK, verified server-side")
        String idToken,

        @Schema(example = "Pixel 8 - Chrome")
        String deviceFingerprint
) {
}
