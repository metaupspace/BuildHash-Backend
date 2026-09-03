package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.InvoiceStatus;

import java.time.Instant;
import java.util.UUID;
import com.builddash.backend.domain.exception.InvalidInvoiceStateException;

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
        if (status != InvoiceStatus.PENDING && status != InvoiceStatus.DLQ_RETRY && status != InvoiceStatus.GENERATING) throw new InvalidInvoiceStateException(status.name(), InvoiceStatus.GENERATING.name());
        return new Invoice(id, orderId, number, InvoiceStatus.GENERATING, storageKey, contentType, generatedAt, attemptCount + 1, createdAt, Instant.now());
    }

    public Invoice markReady(String number, String storageKey) {
        if (status == InvoiceStatus.READY) return this;
        if (status != InvoiceStatus.GENERATING) throw new InvalidInvoiceStateException(status.name(), InvoiceStatus.READY.name());
        return new Invoice(id, orderId, number, InvoiceStatus.READY, storageKey, contentType, Instant.now(), attemptCount, createdAt, Instant.now());
    }

    public Invoice markDlqRetry() {
        if (status == InvoiceStatus.DLQ_RETRY) return this;
        if (status != InvoiceStatus.GENERATING) throw new InvalidInvoiceStateException(status.name(), InvoiceStatus.DLQ_RETRY.name());
        return new Invoice(id, orderId, number, InvoiceStatus.DLQ_RETRY, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }

    public Invoice markPending() {
        if (status == InvoiceStatus.PENDING) return this;
        if (status != InvoiceStatus.GENERATING) throw new InvalidInvoiceStateException(status.name(), InvoiceStatus.PENDING.name());
        return new Invoice(id, orderId, number, InvoiceStatus.PENDING, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }

    public Invoice markFailed() {
        if (status == InvoiceStatus.FAILED) return this;
        if (status != InvoiceStatus.GENERATING && status != InvoiceStatus.PENDING && status != InvoiceStatus.DLQ_RETRY) throw new InvalidInvoiceStateException(status.name(), InvoiceStatus.FAILED.name());
        return new Invoice(id, orderId, number, InvoiceStatus.FAILED, storageKey, contentType, generatedAt, attemptCount, createdAt, Instant.now());
    }
}
