package com.builddash.backend.domain.enums;

/**
 * B2B company roles — responsibility profiles ONLY. No hierarchy, no rank, no
 * atLeast(): capabilities come from the company's company_role_permissions rows
 * (resolved live by B2bAuthorizer), never from the role name itself.
 *
 * OWNER is special by design: its permissions are implicit ALL and immutable
 * (never stored, never editable) so a company can never lock itself out.
 *
 * Distinct from application roles (USER/GUEST/ADMIN -> Spring authorities): these
 * travel in the separate JWT "b2b" claim and never become authorities.
 */
public enum CompanyRole {
    OWNER,
    PROCUREMENT_MANAGER,
    SITE_SUPERVISOR,
    ACCOUNTANT,
    VIEWER
}
