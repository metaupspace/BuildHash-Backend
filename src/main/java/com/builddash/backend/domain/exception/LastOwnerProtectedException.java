package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * Thrown when a membership mutation (remove/demote/owner-transfer) would leave the
 * company without any OWNER member. Evaluated under the company-row lock protocol
 * inside the mutating transaction. Supersedes LastAdminProtectedException (9-A.1:
 * ADMIN is no longer a B2B role).
 */
public class LastOwnerProtectedException extends DomainException {

    public LastOwnerProtectedException(UUID companyId) {
        super("LAST_OWNER_PROTECTED",
                "Company " + companyId + " must keep at least one OWNER member");
    }
}
