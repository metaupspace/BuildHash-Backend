package com.builddash.backend.domain.exception;

/**
 * Duplicate site name within a company — the uq_company_sites_name constraint (V25)
 * surfacing as a 409, same style as MemberAlreadyExistsException.
 */
public class SiteNameTakenException extends DomainException {

    public SiteNameTakenException(String name) {
        super("SITE_NAME_TAKEN", "Site name '" + name + "' already exists in this company");
    }
}
