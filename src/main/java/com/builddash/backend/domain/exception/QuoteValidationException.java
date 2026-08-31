package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * A quote failed a domain rule during submission (inactive vendor, invalid
 * validity) or selection for conversion (expired quote). 422.
 */
public class QuoteValidationException extends DomainException {

    public QuoteValidationException(String code, String message) {
        super(code, message);
    }

    public static QuoteValidationException vendorInactive(UUID vendorId) {
        return new QuoteValidationException("VENDOR_INACTIVE",
                "Vendor is inactive and cannot submit new quotes: " + vendorId);
    }

    public static QuoteValidationException validityInvalid() {
        return new QuoteValidationException("QUOTE_VALIDITY_INVALID",
                "Quote validUntil must be in the future");
    }

    public static QuoteValidationException quoteExpired() {
        return new QuoteValidationException("QUOTE_EXPIRED",
                "Quote has expired and cannot be selected for conversion");
    }
}
