package com.builddash.backend.domain.service;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creation-time default permission profiles ONLY. After company creation the
 * company_role_permissions rows are the single source of truth — an OWNER editing a
 * role's set never touches this class again.
 *
 * OWNER is absent on purpose: its permissions are implicit ALL, never stored.
 * ROLE_PERMISSION_MANAGE appears in no profile — the escalation firewall.
 */
public final class CompanyPermissionDefaults {

    private CompanyPermissionDefaults() {
    }

    private static final Map<CompanyRole, List<CompanyPermission>> DEFAULTS = Map.of(
            CompanyRole.PROCUREMENT_MANAGER, List.of(
                    CompanyPermission.COMPANY_VIEW,
                    CompanyPermission.RFQ_VIEW,
                    CompanyPermission.RFQ_CREATE,
                    CompanyPermission.RFQ_CANCEL,
                    CompanyPermission.RFQ_CONVERT,
                    CompanyPermission.QUOTE_VIEW,
                    CompanyPermission.PO_VIEW,
                    CompanyPermission.PO_UPLOAD,
                    CompanyPermission.PO_CONVERT,
                    CompanyPermission.ORDER_VIEW,
                    CompanyPermission.ORDER_CREATE,
                    CompanyPermission.APPROVAL_VIEW),
            CompanyRole.SITE_SUPERVISOR, List.of(
                    CompanyPermission.COMPANY_VIEW,
                    CompanyPermission.SITE_VIEW,
                    CompanyPermission.ORDER_VIEW,
                    CompanyPermission.PO_VIEW,
                    CompanyPermission.RFQ_VIEW,
                    CompanyPermission.APPROVAL_VIEW),
            CompanyRole.ACCOUNTANT, List.of(
                    CompanyPermission.COMPANY_VIEW,
                    CompanyPermission.ORDER_VIEW,
                    CompanyPermission.INVOICE_VIEW,
                    CompanyPermission.STATEMENT_VIEW),
            CompanyRole.VIEWER, List.of(
                    CompanyPermission.COMPANY_VIEW,
                    CompanyPermission.SITE_VIEW,
                    CompanyPermission.ORDER_VIEW,
                    CompanyPermission.RFQ_VIEW,
                    CompanyPermission.PO_VIEW));

    /** Default set for a non-OWNER role (empty for OWNER — implicit, never stored). */
    public static Set<CompanyPermission> forRole(CompanyRole role) {
        return DEFAULTS.getOrDefault(role, List.of()).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    /** All non-OWNER roles that carry default profiles. */
    public static Set<CompanyRole> customizableRoles() {
        return DEFAULTS.keySet();
    }
}
