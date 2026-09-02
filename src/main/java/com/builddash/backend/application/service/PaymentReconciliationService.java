package com.builddash.backend.application.service;

import java.util.UUID;

public interface PaymentReconciliationService {

    enum ReconciliationOutcome {
        CONFIRMED,
        CANCEL_ELIGIBLE,
        AMBIGUOUS_HOLD
    }

    /**
     * Reconciles a stale PENDING payment with the upstream gateway.
     * Returns:
     * - CONFIRMED if gateway captured payment and order was confirmed;
     * - CANCEL_ELIGIBLE if gateway confirms payment failed/aborted;
     * - AMBIGUOUS_HOLD if gateway response is ambiguous, pending, or timed out (preserves order without cancellation).
     */
    ReconciliationOutcome reconcileStalePendingPayment(UUID orderId);
}
