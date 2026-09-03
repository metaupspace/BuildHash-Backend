package com.builddash.backend.domain.exception;

public class InvalidInvoiceStateException extends DomainException {
    public InvalidInvoiceStateException(String currentStatus, String targetStatus) {
        super("INVALID_INVOICE_STATE", "Cannot transition invoice from " + currentStatus + " to " + targetStatus);
    }
}
