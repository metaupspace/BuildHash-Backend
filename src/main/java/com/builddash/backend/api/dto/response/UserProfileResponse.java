package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.GstinStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "UserProfileResponse")
public record UserProfileResponse(
        UUID id,
        @Schema(example = "+919876543210") String phone,
        @Schema(example = "owner@sharmatraders.in") String email,
        @Schema(example = "Ramesh Sharma") String name,
        @Schema(example = "Sharma Traders") String businessName,
        @Schema(example = "27AAAPZ1234C1Z5") String gstNumber,
        GstinStatus gstinStatus
) {
}
