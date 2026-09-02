package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerQuestionRequest(
        @NotBlank
        @Size(max = 2000, message = "Answer must not exceed 2000 characters")
        @Schema(example = "Yes, this grade is suitable for waterproofing applications.")
        String body
) {
}
