package com.builddash.backend.domain.exception;

public class InvalidPaymentStateException extends DomainException {
    public InvalidPaymentStateException(String currentStatus, String targetStatus) {
        super("INVALID_PAYMENT_STATE", "Cannot transition payment from " + currentStatus + " to " + targetStatus);
    }
}
