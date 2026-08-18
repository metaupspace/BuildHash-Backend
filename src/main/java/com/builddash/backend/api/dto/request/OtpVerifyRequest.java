package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "phone must be 10-15 digits, optionally starting with +")
        @Schema(example = "+919876543210")
        String phone,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "otp must be 6 digits")
        @Schema(example = "123456")
        String otp,

        @Schema(example = "iPhone 15 - Safari", description = "Free-text client-supplied device label, shown later in the device registry")
        String deviceFingerprint
) {
}
