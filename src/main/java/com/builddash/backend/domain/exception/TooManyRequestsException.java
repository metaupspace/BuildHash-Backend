package com.builddash.backend.domain.exception;

public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
