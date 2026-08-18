package com.builddash.backend.api.dto.request;

import com.builddash.backend.application.validator.ValidGstin;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateProfileRequest", description = "Fields left null are left unchanged")
public record UpdateProfileRequest(
        @Schema(example = "Ramesh Sharma") String name,
        @Schema(example = "Sharma Traders") String businessName,
        @ValidGstin
        @Schema(example = "27AAAPZ1234C1Z5", description = "15-character GSTIN, format-validated only")
        String gstNumber
) {
}
