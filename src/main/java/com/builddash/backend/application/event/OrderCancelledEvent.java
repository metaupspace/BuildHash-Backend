package com.builddash.backend.application.event;

import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        OrderCancellationOrigin origin
) {

    /** Which cancellation path fired — the customer window and the warehouse webhook are distinct customer-facing moments. */
    public enum OrderCancellationOrigin {
        CUSTOMER_WINDOW,
        DELIVERY_WEBHOOK
    }
}
