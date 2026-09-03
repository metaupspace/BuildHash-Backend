package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;
import com.builddash.backend.domain.exception.InvalidPaymentStateException;

public record Payment(
        UUID id,
        UUID orderId,
        String transactionId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentUrl
) {
    public Payment markSuccess(String transactionId) {
        if (status == PaymentStatus.SUCCESS) return this;
        if (status != PaymentStatus.PENDING) throw new InvalidPaymentStateException(status.name(), PaymentStatus.SUCCESS.name());
        return new Payment(id, orderId, transactionId, amount, PaymentStatus.SUCCESS, paymentUrl);
    }

    public Payment markFailed(String transactionId) {
        if (status == PaymentStatus.FAILED) return this;
        if (status != PaymentStatus.PENDING) throw new InvalidPaymentStateException(status.name(), PaymentStatus.FAILED.name());
        return new Payment(id, orderId, transactionId, amount, PaymentStatus.FAILED, paymentUrl);
    }
}
