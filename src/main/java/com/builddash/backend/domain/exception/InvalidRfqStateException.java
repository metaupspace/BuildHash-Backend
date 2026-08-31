package com.builddash.backend.domain.exception;

/**
 * An RFQ operation attempted from a non-OPEN (or no longer OPEN) state —
 * including the losing side of a concurrent conversion/expiry race after the
 * row lock is finally acquired. 409.
 */
public class InvalidRfqStateException extends DomainException {

    public InvalidRfqStateException(String code, String message) {
        super(code, message);
    }

    public static InvalidRfqStateException notOpen(String currentStatus) {
        return new InvalidRfqStateException("RFQ_NOT_OPEN",
                "RFQ is not open for this operation, current status: " + currentStatus);
    }
}
