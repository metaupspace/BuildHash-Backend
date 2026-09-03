package com.builddash.backend.domain.exception;

public class InvalidPoImportStateException extends DomainException {
    public InvalidPoImportStateException(String currentStatus, String targetStatus) {
        super("INVALID_PO_IMPORT_STATE", "Cannot transition PO import from " + currentStatus + " to " + targetStatus);
    }
}
