package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * Duplicate company membership — the UNIQUE(company_id, user_id) constraint (V25)
 * surfacing as the existing 409 style (ReturnAlreadyExistsException precedent):
 * the application check answers the common path, the constraint answers the race.
 */
public class MemberAlreadyExistsException extends DomainException {

    public MemberAlreadyExistsException(UUID companyId, UUID userId) {
        super("MEMBER_ALREADY_EXISTS",
                "User " + userId + " is already a member of company " + companyId);
    }
}
