package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthTokensResponse")
public record AuthTokensResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresInSeconds
) {
}
