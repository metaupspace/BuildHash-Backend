package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken
) {
}
