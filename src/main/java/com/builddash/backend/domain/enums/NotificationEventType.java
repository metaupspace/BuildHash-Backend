package com.builddash.backend.domain.enums;

import java.util.Map;

/**
 * One value per customer-facing notification moment, NOT per Spring event record. The
 * (eventType, referenceId) idempotency guard needs per-moment granularity: a single
 * RETURN_STATUS_CHANGED value would make the guard suppress every return transition after
 * the first, since all of them share one returnId. Return state transitions are one-way,
 * so each value below fires at most once per return — the guard stays sound.
 *
 * CART_ABANDONED is the one recurring moment (OQ-11's SMS side, wired in Checkpoint C):
 * a stable PRIMARY cartId re-abandons after renewed activity, so it dedupes via
 * notifyRecurring's cooldown window rather than the plain existence check.
 */
public enum NotificationEventType {

    ORDER_PACKED(NotificationChannel.WHATSAPP),
    ORDER_DISPATCHED(NotificationChannel.WHATSAPP),
    ORDER_DELIVERED(NotificationChannel.WHATSAPP),
    ORDER_CANCELLED(NotificationChannel.WHATSAPP),

    RETURN_APPROVED(NotificationChannel.WHATSAPP),
    RETURN_PICKUP_SCHEDULED(NotificationChannel.WHATSAPP),
    RETURN_PICKED_UP(NotificationChannel.WHATSAPP),
    RETURN_IN_QC(NotificationChannel.WHATSAPP),
    RETURN_REJECTED(NotificationChannel.WHATSAPP),
    REFUND_INITIATED(NotificationChannel.WHATSAPP),

    REFUND_COMPLETED(NotificationChannel.WHATSAPP),
    INVOICE_READY(NotificationChannel.WHATSAPP),

    CART_ABANDONED(NotificationChannel.SMS);

    private final NotificationChannel channel;

    NotificationEventType(NotificationChannel channel) {
        this.channel = channel;
    }

    /** The locked template-to-channel mapping (OQ-11) — one place, no map class. */
    public NotificationChannel channel() {
        return channel;
    }

    private static final Map<ReturnStatus, NotificationEventType> FROM_RETURN_STATUS = Map.of(
            ReturnStatus.APPROVED, RETURN_APPROVED,
            ReturnStatus.PICKUP_SCHEDULED, RETURN_PICKUP_SCHEDULED,
            ReturnStatus.PICKED_UP, RETURN_PICKED_UP,
            ReturnStatus.QC, RETURN_IN_QC,
            ReturnStatus.REJECTED, RETURN_REJECTED,
            ReturnStatus.REFUND_INITIATED, REFUND_INITIATED);

    /**
     * The listener's {@code to -> template} mapping (locked decision 2.1). Returns null for
     * REQUESTED (never published — the customer's own action) and REFUND_COMPLETED (that
     * moment is owned by the RefundCompletedEvent handler, not the status-change handler —
     * mapping it here would double-notify from completeRefund's two events).
     */
    public static NotificationEventType fromReturnStatus(ReturnStatus to) {
        return FROM_RETURN_STATUS.get(to);
    }
}
