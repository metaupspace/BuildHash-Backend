package com.builddash.backend.domain.exception;

public class CheckoutValidationException extends DomainException {
    public CheckoutValidationException(String code, String message) {
        super(code, message);
    }
}
