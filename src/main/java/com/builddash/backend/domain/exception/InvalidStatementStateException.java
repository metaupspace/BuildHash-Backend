package com.builddash.backend.domain.exception;

public class InvalidStatementStateException extends DomainException {
    public InvalidStatementStateException(String currentStatus, String targetStatus) {
        super("INVALID_STATEMENT_STATE", "Cannot transition statement from " + currentStatus + " to " + targetStatus);
    }
}
