package com.builddash.backend.domain.exception;

/**
 * A vendor already quoted this RFQ — one quote per (rfq, vendor), enforced
 * finally by the UNIQUE constraint. 409 DUPLICATE_QUOTE.
 */
public class DuplicateQuoteException extends DomainException {

    public DuplicateQuoteException() {
        super("DUPLICATE_QUOTE", "A quote from this vendor already exists for this RFQ");
    }
}
