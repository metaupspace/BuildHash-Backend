package com.builddash.backend.domain.exception;

public class LockedException extends DomainException {

    public LockedException(String code, String message) {
        super(code, message);
    }
}
