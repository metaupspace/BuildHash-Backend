package com.builddash.backend.domain.exception;

public class InvalidDeleteRequestStateException extends DomainException {
    public InvalidDeleteRequestStateException(String currentStatus, String targetStatus) {
        super("INVALID_DELETE_REQUEST_STATE", "Cannot transition delete request from " + currentStatus + " to " + targetStatus);
    }
}
