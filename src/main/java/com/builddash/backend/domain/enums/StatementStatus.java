package com.builddash.backend.domain.enums;

/** Statement generation lifecycle (9-E) — mirrors InvoiceStatus: failures are
 *  retryable (PENDING reclaim / stale GENERATING), never a persisted FAILED state. */
public enum StatementStatus {
    PENDING,
    GENERATING,
    READY,
    DLQ_RETRY
}
