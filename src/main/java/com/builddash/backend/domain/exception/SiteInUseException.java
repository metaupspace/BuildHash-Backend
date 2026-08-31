package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * Thrown when deactivating a company site that is still referenced by non-CANCELLED
 * orders. Checked under the site row lock so it cannot race a new order-site
 * association (the 9-B/9-C association contract locks the same row first).
 */
public class SiteInUseException extends DomainException {

    public SiteInUseException(UUID siteId, long activeOrders) {
        super("SITE_IN_USE",
                "Site " + siteId + " is referenced by " + activeOrders + " active order(s) and cannot be deactivated");
    }
}
