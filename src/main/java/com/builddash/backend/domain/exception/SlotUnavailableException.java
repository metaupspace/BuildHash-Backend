package com.builddash.backend.domain.exception;

public class SlotUnavailableException extends DomainException {
    public SlotUnavailableException(String code, String message) {
        super(code, message);
    }
}
