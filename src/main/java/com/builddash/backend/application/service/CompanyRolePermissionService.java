package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CompanyRolePermissionService {

    /** All four customizable roles with their effective sets; OWNER reported as implicit ALL + immutable flag. */
    Map<CompanyRole, RolePermissionView> effectivePermissions(UUID companyId, UUID actorUserId);

    /** OWNER-only full-set replacement for one non-OWNER role. */
    void replacePermissions(UUID companyId, UUID actorUserId, CompanyRole role, Set<CompanyPermission> permissions);

    record RolePermissionView(Set<CompanyPermission> permissions, boolean immutable) {
    }
}
