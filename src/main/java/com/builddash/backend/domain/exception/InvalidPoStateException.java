package com.builddash.backend.domain.exception;

/**
 * A PO import operation attempted from a state that does not admit it —
 * conversion requires REVIEW (409 PO_IMPORT_NOT_REVIEW) and at least one valid
 * row (409 NO_VALID_ROWS).
 */
public class InvalidPoStateException extends DomainException {

    public InvalidPoStateException(String code, String message) {
        super(code, message);
    }
}
