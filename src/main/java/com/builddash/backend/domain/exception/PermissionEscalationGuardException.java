package com.builddash.backend.domain.exception;

import com.builddash.backend.domain.enums.CompanyPermission;

/**
 * The self-escalation firewall: ROLE_PERMISSION_MANAGE may never be granted to a
 * non-OWNER role. Without this guard, an OWNER could hand permission-administration
 * to a role that then grants itself everything.
 */
public class PermissionEscalationGuardException extends DomainException {

    public PermissionEscalationGuardException() {
        super("PERMISSION_ESCALATION_GUARDED",
                CompanyPermission.ROLE_PERMISSION_MANAGE.name() + " cannot be granted to a non-OWNER role");
    }
}
