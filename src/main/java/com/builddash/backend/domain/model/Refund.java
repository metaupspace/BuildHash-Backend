package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.builddash.backend.domain.exception.InvalidRefundStateException;

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
        if (status == RefundStatus.SUCCESS) return this;
        if (status != RefundStatus.PENDING) throw new InvalidRefundStateException(status.name(), RefundStatus.SUCCESS.name());
        return new Refund(id, returnId, paymentTransactionId, amount, RefundStatus.SUCCESS, gatewayRefundId != null ? gatewayRefundId : this.gatewayRefundId, createdAt, Instant.now());
    }

    public Refund markFailed(String gatewayRefundId) {
        if (status == RefundStatus.FAILED) return this;
        if (status != RefundStatus.PENDING) throw new InvalidRefundStateException(status.name(), RefundStatus.FAILED.name());
        return new Refund(id, returnId, paymentTransactionId, amount, RefundStatus.FAILED, gatewayRefundId != null ? gatewayRefundId : this.gatewayRefundId, createdAt, Instant.now());
    }
}
