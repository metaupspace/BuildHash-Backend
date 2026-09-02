package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.RefundReference;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentGateway {
    PaymentReference initiate(UUID orderId, BigDecimal amount);

    /**
     * returnId is a durable correlation key the gateway is expected to echo back in its
     * refund webhook. Without it, a crash between gateway success and local finalize can
     * lose the gatewayRefundId forever with no way to recover the claim (H1.4).
     */
    RefundReference refund(String transactionId, BigDecimal amount, UUID returnId);

    /**
     * Queries upstream payment gateway provider status for an existing transaction/order.
     * Used by reconciliation schedulers before cancelling stale orders or handling crashes.
     */
    Optional<PaymentStatus> queryStatus(String transactionId, UUID orderId);
}
