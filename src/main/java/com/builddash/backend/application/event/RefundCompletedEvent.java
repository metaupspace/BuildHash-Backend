package com.builddash.backend.application.event;

import java.util.UUID;

/**
 * Fired from the REAL refund success path (RefundWebhookServiceImpl after completeRefund), NOT from
 * RefundWebhookEvent — that record is dummy-gateway simulation only (PLAN_PHASE7 Section 1).
 */
public record RefundCompletedEvent(
        UUID returnId,
        UUID refundId
) {}
