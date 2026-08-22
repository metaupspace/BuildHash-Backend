package com.builddash.backend.domain.exception;

import com.builddash.backend.domain.enums.OrderStatus;

public class InvalidOrderStateException extends DomainException {
    public InvalidOrderStateException(String currentStatus, String targetStatus) {
        super("INVALID_ORDER_STATE", "Cannot transition order from " + currentStatus + " to " + targetStatus);
    }

    public InvalidOrderStateException(OrderStatus currentStatus) {
        super("ORDER_ALREADY_" + currentStatus.name(), "Cannot retry payment for order in state " + currentStatus.name());
    }
}
