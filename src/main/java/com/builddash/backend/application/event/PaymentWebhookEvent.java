package com.builddash.backend.application.event;

import java.util.UUID;

public record PaymentWebhookEvent(
        UUID orderId,
        String status, // "SUCCESS" or "FAILED"
        String signature
) {
}
