package com.builddash.backend.domain.enums;

public enum PaymentReconciliationType {
    CAPTURED_ON_CANCELLED_ORDER,
    ORPHANED_GATEWAY_SESSION,
    STALE_PENDING_GATEWAY_MISMATCH
}
