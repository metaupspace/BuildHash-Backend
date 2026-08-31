package com.builddash.backend.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A company's physical operating site. Company-scoped with a unique name per company.
 * Deactivation (not deletion) is the lifecycle end for sites with history: an active
 * site referenced by non-CANCELLED orders cannot be deactivated — enforced under the
 * site row lock in CompanySiteServiceImpl (9-A) and the mirrored lock contract that
 * 9-B/9-C order-association flows must follow.
 */
public record CompanySite(
        UUID id,
        UUID companyId,
        String name,
        UUID addressId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public CompanySite deactivate() {
        return new CompanySite(id, companyId, name, addressId, false, createdAt, updatedAt);
    }
}
