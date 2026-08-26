package com.builddash.backend.domain.exception;

public class InvalidReturnStateException extends DomainException {
    public InvalidReturnStateException(String currentStatus, String targetStatus) {
        super("INVALID_RETURN_STATE", "Cannot transition return from " + currentStatus + " to " + targetStatus);
    }
}
