package com.builddash.backend.domain.enums;

public enum InvoiceStatus {
    PENDING,
    GENERATING,
    READY,
    DLQ_RETRY,
    FAILED
}
