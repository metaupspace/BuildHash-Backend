package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Refund(
        UUID id,
        UUID returnId,
        String paymentTransactionId,
        BigDecimal amount,
        RefundStatus status,
        String gatewayRefundId,
        Instant createdAt,
        Instant updatedAt
) {
    public Refund markSuccess(String gatewayRefundId) {
        return new Refund(id, returnId, paymentTransactionId, amount, RefundStatus.SUCCESS, gatewayRefundId != null ? gatewayRefundId : this.gatewayRefundId, createdAt, Instant.now());
    }

    public Refund markFailed(String gatewayRefundId) {
        return new Refund(id, returnId, paymentTransactionId, amount, RefundStatus.FAILED, gatewayRefundId != null ? gatewayRefundId : this.gatewayRefundId, createdAt, Instant.now());
    }
}
