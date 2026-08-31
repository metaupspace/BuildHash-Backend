package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.ReplaceRolePermissionsRequest;
import com.builddash.backend.api.dto.response.RolePermissionsResponse;
import com.builddash.backend.application.service.CompanyRolePermissionService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/role-permissions")
@Tag(name = "Company Role Permissions", description = "OWNER-only customization of per-role B2B permissions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanyRolePermissionController {

    private final CompanyRolePermissionService rolePermissionService;

    @GetMapping
    @Operation(summary = "Effective permission sets per role (ROLE_PERMISSION_MANAGE)")
    public RolePermissionsResponse get(@PathVariable UUID companyId,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return RolePermissionsResponse.from(rolePermissionService.effectivePermissions(companyId, user.userId()));
    }

    @PutMapping("/{role}")
    @Operation(summary = "Replace a non-OWNER role's permission set (ROLE_PERMISSION_MANAGE; OWNER is immutable)")
    public RolePermissionsResponse replace(@PathVariable UUID companyId,
                                           @PathVariable com.builddash.backend.domain.enums.CompanyRole role,
                                           @Valid @RequestBody ReplaceRolePermissionsRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        rolePermissionService.replacePermissions(companyId, user.userId(), role, request.permissions());
        return RolePermissionsResponse.from(rolePermissionService.effectivePermissions(companyId, user.userId()));
    }
}
