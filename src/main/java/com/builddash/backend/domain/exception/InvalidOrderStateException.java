package com.builddash.backend.domain.exception;

public class InvalidOrderStateException extends DomainException {
    public InvalidOrderStateException(String currentStatus, String targetStatus) {
        super("INVALID_ORDER_STATE", "Cannot transition order from " + currentStatus + " to " + targetStatus);
    }
}
