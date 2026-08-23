package com.builddash.backend.domain.exception;

import java.util.UUID;

public class PaymentGatewayException extends DomainException {
    
    private final UUID orderId;

    public PaymentGatewayException(UUID orderId, String message) {
        super("PAYMENT_GATEWAY_DOWN", message);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
