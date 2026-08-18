package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "NotifyMeSubscriptionResponse")
public record NotifyMeSubscriptionResponse(
        UUID productId,
        Instant createdAt
) {
}
