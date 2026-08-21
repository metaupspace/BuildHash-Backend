package com.builddash.backend.domain.exception;

import lombok.Getter;
import java.util.UUID;

@Getter
public class PaymentGatewayException extends DomainException {
    
    private final UUID orderId;

    public PaymentGatewayException(UUID orderId, String message) {
        super("PAYMENT_GATEWAY_DOWN", message);
        this.orderId = orderId;
    }
}
