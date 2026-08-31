package com.builddash.backend.domain.exception;

import com.builddash.backend.domain.enums.CompanyRole;

/**
 * Thrown when a permission-management operation targets the OWNER role. OWNER
 * permissions are implicit ALL and immutable — there is nothing to edit, and the
 * database CHECK makes OWNER rows unrepresentable in the first place.
 */
public class OwnerPermissionsImmutableException extends DomainException {

    public OwnerPermissionsImmutableException() {
        super("OWNER_PERMISSIONS_IMMUTABLE",
                "OWNER permissions are implicit and cannot be modified" + suffix());
    }

    private static String suffix() {
        return " (role " + CompanyRole.OWNER.name() + ")";
    }
}
