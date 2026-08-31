package com.builddash.backend.api.dto.response;

import com.builddash.backend.application.service.CompanyRolePermissionService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RolePermissionsResponse(
        Map<CompanyRole, RoleView> roles
) {

    public record RoleView(Set<CompanyPermission> permissions, boolean immutable) {
    }

    public static RolePermissionsResponse from(Map<CompanyRole, CompanyRolePermissionService.RolePermissionView> view) {
        Map<CompanyRole, RoleView> roles = new LinkedHashMap<>();
        view.forEach((role, v) -> roles.put(role, new RoleView(v.permissions(), v.immutable())));
        return new RolePermissionsResponse(roles);
    }
}
