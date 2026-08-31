package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * The vendor is not in the RFQ's creation-time routing snapshot — there is no
 * unrouted override in 9-B. 422 VENDOR_NOT_ROUTED.
 */
public class VendorNotRoutableException extends DomainException {

    public VendorNotRoutableException(UUID vendorId, UUID rfqId) {
        super("VENDOR_NOT_ROUTED",
                "Vendor " + vendorId + " is not routed to RFQ " + rfqId);
    }
}
