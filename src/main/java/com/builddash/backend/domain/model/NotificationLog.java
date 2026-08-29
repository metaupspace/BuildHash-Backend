package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox-style tracking row (catalog-outbox precedent, PLAN_PHASE7 5(c)): written PENDING in
 * the trigger listener's transaction, flipped SENT/FAILED by the queue consumer, swept later.
 * The (eventType, referenceId) existence check is the idempotency guard for every trigger.
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

    private UUID id;
    private UUID userId;

    /** PII snapshot at send time — the account-deletion flow must eventually account for these. */
    private String recipientPhone;

    private NotificationChannel channel;
    private NotificationEventType eventType;
    private UUID referenceId;
    private NotificationStatus status = NotificationStatus.PENDING;

    private Instant sentAt;

    /** Reserved, unwired until the real-vendor gateway receipt webhook exists. */
    private Instant deliveredAt;

    private Instant createdAt;
    private Instant updatedAt;
}
