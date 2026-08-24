package com.builddash.backend.domain.exception;

public class PaymentRetryInProgressException extends DomainException {
    public PaymentRetryInProgressException() {
        super("RETRY_ALREADY_IN_PROGRESS", "A payment retry is already in progress for this order.");
    }
}
