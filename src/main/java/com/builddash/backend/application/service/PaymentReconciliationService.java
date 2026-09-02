package com.builddash.backend.application.service;

import java.util.UUID;

public interface PaymentReconciliationService {

    /**
     * Reconciles a stale PENDING payment with the upstream gateway.
     * Returns true if the order was confirmed via gateway reconciliation;
     * returns false if payment failed, is absent, or remains ambiguous.
     */
    boolean reconcileStalePendingPayment(UUID orderId);
}
