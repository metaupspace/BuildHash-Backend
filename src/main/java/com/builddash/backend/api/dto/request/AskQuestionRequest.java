package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
        @NotBlank
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        @Schema(example = "Does this cement work for waterproofing?")
        String body
) {
}
