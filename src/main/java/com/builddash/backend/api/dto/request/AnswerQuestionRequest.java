package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AnswerQuestionRequest(
        @NotBlank
        @Schema(example = "Yes, this grade is suitable for waterproofing applications.")
        String body
) {
}
