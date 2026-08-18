package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.AnswerSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AnswerResponse")
public record AnswerResponse(
        UUID id,
        UUID userId,
        String body,
        AnswerSource source,
        Instant createdAt
) {
}
