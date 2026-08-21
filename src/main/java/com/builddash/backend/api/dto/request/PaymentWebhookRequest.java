package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentWebhookRequest(
        @NotNull UUID orderId,
        @NotBlank String status,
        @NotBlank String signature
) {
}
