package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanyRolePermissionService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.OwnerPermissionsImmutableException;
import com.builddash.backend.domain.exception.PermissionEscalationGuardException;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OWNER-only permission administration. Both operations require
 * ROLE_PERMISSION_MANAGE — a permission only OWNER holds (implicitly), which is what
 * makes "only OWNER can change permissions" structural rather than a soft default:
 * even if some future edit granted MEMBER_MANAGE elsewhere, ROLE_PERMISSION_MANAGE
 * can never be granted (firewall below), so this surface stays OWNER-only.
 */
@Service
@RequiredArgsConstructor
public class CompanyRolePermissionServiceImpl implements CompanyRolePermissionService {

    private final B2bAuthorizer authorizer;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<CompanyRole, RolePermissionView> effectivePermissions(UUID companyId, UUID actorUserId) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.ROLE_PERMISSION_MANAGE, null, false);

        Map<CompanyRole, RolePermissionView> view = new LinkedHashMap<>();
        view.put(CompanyRole.OWNER, new RolePermissionView(
                Set.of(CompanyPermission.values()), true));
        for (CompanyRole role : CompanyRole.values()) {
            if (role == CompanyRole.OWNER) {
                continue;
            }
            view.put(role, new RolePermissionView(
                    companyRolePermissionRepository.findPermissions(companyId, role), false));
        }
        return view;
    }

    @Override
    @Transactional
    public void replacePermissions(UUID companyId, UUID actorUserId, CompanyRole role,
                                   Set<CompanyPermission> permissions) {
        // critical=true: company-row lock first — concurrent PUTs (and any concurrent
        // critical authorization) serialize here; last committed valid set wins.
        authorizer.authorize(actorUserId, companyId, CompanyPermission.ROLE_PERMISSION_MANAGE,
                null, true);

        if (role == CompanyRole.OWNER) {
            throw new OwnerPermissionsImmutableException();
        }
        if (permissions.contains(CompanyPermission.ROLE_PERMISSION_MANAGE)) {
            throw new PermissionEscalationGuardException();
        }

        companyRolePermissionRepository.replaceRolePermissions(companyId, role, permissions);
    }
}
