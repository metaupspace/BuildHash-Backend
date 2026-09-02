package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.InvoiceStatus;

import java.time.Instant;
import java.util.UUID;

public record Invoice(
        UUID id,
        UUID orderId,
        String number,
        InvoiceStatus status,
        String storageKey,
        String contentType,
        Instant generatedAt,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
    public Invoice claim() {
        return new Invoice(id, orderId, number, InvoiceStatus.GENERATING, storageKey, contentType, generatedAt, attemptCount + 1, createdAt, Instant.now());
    }

    public Invoice markReady(String number, String storageKey) {
        return new Invoice(id, orderId, number, InvoiceStatus.READY, storageKey, contentType, Instant.now(), attemptCount, createdAt, Instant.now());
    }

    public Invoice markDlqRetry() {
        return new Invoice(id, orderId, number, InvoiceStatus.DLQ_RETRY, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }

    public Invoice markPending() {
        return new Invoice(id, orderId, number, InvoiceStatus.PENDING, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }

    public Invoice markFailed() {
        return new Invoice(id, orderId, number, InvoiceStatus.FAILED, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }
}
