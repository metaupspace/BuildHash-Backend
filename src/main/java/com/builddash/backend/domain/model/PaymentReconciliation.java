package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PaymentReconciliationStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliation(
        UUID id,
        UUID orderId,
        UUID paymentId,
        String transactionId,
        BigDecimal amount,
        PaymentReconciliationType reconciliationType,
        PaymentReconciliationStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public PaymentReconciliation resolve(String resolutionNotes) {
        return new PaymentReconciliation(
                id, orderId, paymentId, transactionId, amount,
                reconciliationType, PaymentReconciliationStatus.RESOLVED,
                resolutionNotes, createdAt, Instant.now()
        );
    }
}
