package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OtpSendResponse")
public record OtpSendResponse(
        @Schema(example = "OTP sent") String message,
        @Schema(example = "300") long expiresInSeconds,
        @Schema(example = "false", description = "Bloom-filter hint only — a false positive is possible, "
                + "never a false negative once the phone has completed at least one verify")
        boolean existingUser
) {
}
