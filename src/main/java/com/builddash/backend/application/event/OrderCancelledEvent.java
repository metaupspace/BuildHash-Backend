package com.builddash.backend.application.event;

import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        OrderCancellationOrigin origin
) {

    /**
     * Which cancellation path fired — the customer window and the warehouse webhook are distinct customer-facing moments.
     * 9-D adds the approval-gate origins: APPROVAL_SLOT_UNAVAILABLE (approval won but the
     * delivery slot was gone) and APPROVAL_REJECTED (the approver said no). No parallel
     * cancellation event subsystem — same record, same listener collapse.
     */
    public enum OrderCancellationOrigin {
        CUSTOMER_WINDOW,
        DELIVERY_WEBHOOK,
        APPROVAL_SLOT_UNAVAILABLE,
        APPROVAL_REJECTED
    }
}
