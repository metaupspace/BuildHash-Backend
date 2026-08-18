package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpSendRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "phone must be 10-15 digits, optionally starting with +")
        @Schema(example = "+919876543210")
        String phone
) {
}
