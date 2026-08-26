package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefundWebhookRequest(
        @NotNull UUID returnId,
        @NotBlank String gatewayRefundId,
        @NotBlank String status,
        @NotBlank String signature
) {}
