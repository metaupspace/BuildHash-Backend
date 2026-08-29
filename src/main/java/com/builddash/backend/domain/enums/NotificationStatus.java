package com.builddash.backend.domain.enums;

/**
 * PENDING/SENT/FAILED only (PLAN_PHASE7 Section 4). DELIVERED plus a gateway receipt webhook
 * are reserved for the real-vendor phase — the delivered_at column exists, the status does not.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
