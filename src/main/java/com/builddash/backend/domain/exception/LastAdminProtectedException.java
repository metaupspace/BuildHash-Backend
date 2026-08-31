package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * Thrown when a membership mutation (remove/demote/owner-transfer) would leave the
 * company without any OWNER or ADMIN. Evaluated under pessimistic row locks inside the
 * mutating transaction (CompanyMembershipServiceImpl) — see V25/PLAN notes for the
 * lock protocol. Status mapping lives in GlobalExceptionHandler, not here.
 */
public class LastAdminProtectedException extends DomainException {

    public LastAdminProtectedException(UUID companyId) {
        super("LAST_ADMIN_PROTECTED",
                "Company " + companyId + " must keep at least one OWNER or ADMIN member");
    }
}
