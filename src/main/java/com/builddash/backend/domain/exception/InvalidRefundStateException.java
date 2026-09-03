package com.builddash.backend.domain.exception;

public class InvalidRefundStateException extends DomainException {
    public InvalidRefundStateException(String currentStatus, String targetStatus) {
        super("INVALID_REFUND_STATE", "Cannot transition refund from " + currentStatus + " to " + targetStatus);
    }
}
