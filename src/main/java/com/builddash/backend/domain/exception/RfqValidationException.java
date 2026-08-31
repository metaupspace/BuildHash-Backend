package com.builddash.backend.domain.exception;

/**
 * RFQ creation input violated a domain rule (empty items, non-positive
 * quantity, non-future expiry). Missing products stay NotFoundException. 422.
 */
public class RfqValidationException extends DomainException {

    public RfqValidationException(String code, String message) {
        super(code, message);
    }
}
