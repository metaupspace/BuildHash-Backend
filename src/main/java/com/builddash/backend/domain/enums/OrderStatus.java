package com.builddash.backend.domain.enums;

public enum OrderStatus {
    PAYMENT_PENDING,
    /** B2B approval gate (9-D): born here when the company policy matches; no payment row,
     *  no gateway call, no held slot until approval resumes payment. B2C never enters it. */
    PENDING_APPROVAL,
    CONFIRMED,
    PACKED,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
