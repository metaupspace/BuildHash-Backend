package com.builddash.backend.application.event;

import java.util.UUID;

public record RefundWebhookEvent(
        UUID returnId,
        String gatewayRefundId,
        String status, // "SUCCESS" or "FAILED"
        String signature
) {}
