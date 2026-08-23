package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record Payment(
        UUID id,
        UUID orderId,
        String transactionId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentUrl
) {
    public Payment markSuccess(String transactionId) {
        return new Payment(id, orderId, transactionId, amount, PaymentStatus.SUCCESS, paymentUrl);
    }

    public Payment markFailed(String transactionId) {
        return new Payment(id, orderId, transactionId, amount, PaymentStatus.FAILED, paymentUrl);
    }
}
