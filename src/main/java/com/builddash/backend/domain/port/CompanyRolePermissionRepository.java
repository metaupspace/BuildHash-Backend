package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;

import java.util.Set;
import java.util.UUID;

/**
 * Company-scoped effective permissions for non-OWNER roles. OWNER is never stored —
 * B2bAuthorizer grants it implicitly — so this port's contract is meaningless for
 * OWNER and implementations may reject it.
 */
public interface CompanyRolePermissionRepository {

    Set<CompanyPermission> findPermissions(UUID companyId, CompanyRole role);

    /** Atomic full-set replacement (delete + insert) inside the caller's transaction. */
    void replaceRolePermissions(UUID companyId, CompanyRole role, Set<CompanyPermission> permissions);

    /** Roles currently holding a permission — approval eligibility (9-D). */
    Set<CompanyRole> findRolesWithPermission(UUID companyId, CompanyPermission permission);
}
