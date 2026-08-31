package com.builddash.backend.domain.exception;

/**
 * Whole-file (structural) rejection of a PO upload — malformed workbook, header
 * violations, empty/oversized file, bad content signature, row cap exceeded.
 * The import is persisted as FAILED_STRUCTURE and the company + Idempotency-Key
 * pair stays consumed. 400.
 */
public class PoImportValidationException extends DomainException {

    public PoImportValidationException(String code, String message) {
        super(code, message);
    }
}
