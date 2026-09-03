package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.DeleteRequestStatus;

import java.time.Instant;
import java.util.UUID;
import com.builddash.backend.domain.exception.InvalidDeleteRequestStateException;

/**
 * DPDP account-deletion request (PLAN_PHASE8 decision 9): one pending request per user
 * (service-level 409 + partial unique index backstop), deletionScheduledAt = requestedAt +
 * configured grace days. The sweeper moves PENDING due requests to PROCESSED after executing
 * the per-table classification; re-processing a PROCESSED request is a no-op.
 */
public record DeleteRequest(
        UUID id,
        UUID userId,
        Instant requestedAt,
        Instant deletionScheduledAt,
        Instant processedAt,
        DeleteRequestStatus status
) {

    public static DeleteRequest pending(UUID id, UUID userId, Instant requestedAt, Instant deletionScheduledAt) {
        return new DeleteRequest(id, userId, requestedAt, deletionScheduledAt, null, DeleteRequestStatus.PENDING);
    }

    public boolean isDue(Instant now) {
        return status == DeleteRequestStatus.PENDING && !now.isBefore(deletionScheduledAt);
    }

    public DeleteRequest markProcessed(Instant now) {
        if (status == DeleteRequestStatus.PROCESSED) return this;
        if (status != DeleteRequestStatus.PENDING) throw new InvalidDeleteRequestStateException(status.name(), DeleteRequestStatus.PROCESSED.name());
        return new DeleteRequest(id, userId, requestedAt, deletionScheduledAt, now, DeleteRequestStatus.PROCESSED);
    }
}
