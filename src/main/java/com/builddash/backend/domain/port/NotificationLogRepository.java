package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.model.NotificationLog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository {

    NotificationLog save(NotificationLog log);

    /** For the PII backfill sweep's load-then-re-save; not used by dispatch paths. */
    java.util.Optional<NotificationLog> findById(UUID id);

    /** The idempotency guard — same (eventType, referenceId) shape as OrderConfirmedInvoiceListener's findByOrderId check. */
    boolean existsByEventTypeAndReferenceId(NotificationEventType eventType, UUID referenceId);

    /** The cooldown guard for recurring moments — true only if a row exists inside the window. */
    boolean existsByEventTypeAndReferenceIdAndCreatedAtAfter(NotificationEventType eventType, UUID referenceId, Instant cutoff);

    /** Stuck rows for the reconciliation sweep: PENDING past the threshold, i.e. lost confirms. */
    List<NotificationLog> findStalePending(Instant cutoff);

    /** Bulk update, not fetch-then-mutate — set by the queue consumer after the channel adapter acks. */
    void markSent(UUID id);

    /** Bulk update — set by the DLQ listener after broker retry exhaustion (3 attempts, locked 5(d)). */
    void markFailed(UUID id);

    /** DPDP export: every notification the user was sent. */
    List<NotificationLog> findAllByUserId(UUID userId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)). */
    void deleteByUserId(UUID userId);
}
