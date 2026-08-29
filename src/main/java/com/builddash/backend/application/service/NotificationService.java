package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.NotificationEventType;

import java.time.Duration;
import java.util.UUID;

/**
 * The single internal entry point for sending a notification (PLAN_PHASE7 Section 6 — no
 * HTTP surface). Every trigger routes through here: the event listener since Checkpoint B,
 * the cart-abandonment job and the PENDING-row sweeper since Checkpoint C.
 */
public interface NotificationService {

    /**
     * One-way-transition moments: skip if ANY row for (eventType, referenceId) ever existed.
     * Correct only for moments that genuinely happen once — state-machine transitions.
     */
    void notify(UUID userId, NotificationEventType eventType, UUID referenceId);

    /**
     * Recurring moments (CART_ABANDONED): skip only if a row exists with createdAt inside
     * the cooldown window. Cooldown policy belongs to the caller that knows it re-fires
     * (a scheduler-driven trigger), not to event-type metadata — hence a second method
     * rather than an enum attribute. Cooldown compares against createdAt (queue time),
     * NOT sentAt: createdAt is NOT NULL on every row, and a PENDING row means a
     * notification is already in flight — re-firing behind it would stack duplicates.
     */
    void notifyRecurring(UUID userId, NotificationEventType eventType, UUID referenceId, Duration cooldown);
}
