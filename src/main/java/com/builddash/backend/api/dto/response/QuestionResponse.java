package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "QuestionResponse")
public record QuestionResponse(
        UUID id,
        UUID userId,
        String body,
        Instant createdAt,
        List<AnswerResponse> answers
) {
}
