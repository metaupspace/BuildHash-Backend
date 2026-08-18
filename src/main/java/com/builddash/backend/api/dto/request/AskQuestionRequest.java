package com.builddash.backend.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(
        @NotBlank
        @Schema(example = "Does this cement work for waterproofing?")
        String body
) {
}
