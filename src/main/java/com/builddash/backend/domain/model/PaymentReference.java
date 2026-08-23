package com.builddash.backend.domain.model;

public record PaymentReference(
        String transactionId,
        String paymentUrl
) {
}
