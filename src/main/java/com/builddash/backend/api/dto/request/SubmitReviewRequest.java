package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @Min(1) @Max(5)
        @Schema(example = "5")
        int rating,

        @Size(max = 2000, message = "Comment must not exceed 2000 characters")
        @Schema(example = "Great quality cement, sets fast.")
        String comment
) {
}
