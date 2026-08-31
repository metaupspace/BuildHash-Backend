package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.OwnerPermissionsImmutableException;
import com.builddash.backend.domain.exception.PermissionEscalationGuardException;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyRolePermissionServiceTest {

    @Mock
    private B2bAuthorizer authorizer;

    @Mock
    private CompanyRolePermissionRepository permissionRepository;

    @InjectMocks
    private CompanyRolePermissionServiceImpl service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void get_authorizesWithRolePermissionManage_reportsOwnerImplicitAll() {
        // GET resolves every non-OWNER role's effective set
        for (CompanyRole role : CompanyRole.values()) {
            if (role != CompanyRole.OWNER) {
                when(permissionRepository.findPermissions(companyId, role)).thenReturn(Set.of());
            }
        }

        var view = service.effectivePermissions(companyId, actorId);

        verify(authorizer).authorize(actorId, companyId, CompanyPermission.ROLE_PERMISSION_MANAGE, null, false);
        assertThat(view.get(CompanyRole.OWNER).immutable()).isTrue();
        assertThat(view.get(CompanyRole.OWNER).permissions()).containsExactlyInAnyOrder(CompanyPermission.values());
        assertThat(view.get(CompanyRole.VIEWER).immutable()).isFalse();
    }

    @Test
    void put_ownerRoleRejected_immutable() {
        assertThatThrownBy(() -> service.replacePermissions(companyId, actorId, CompanyRole.OWNER,
                Set.of(CompanyPermission.COMPANY_VIEW)))
                .isInstanceOf(OwnerPermissionsImmutableException.class);
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void put_grantingRolePermissionManageRejected_firewall() {
        assertThatThrownBy(() -> service.replacePermissions(companyId, actorId, CompanyRole.VIEWER,
                Set.of(CompanyPermission.COMPANY_VIEW, CompanyPermission.ROLE_PERMISSION_MANAGE)))
                .isInstanceOf(PermissionEscalationGuardException.class);
        verify(permissionRepository, never()).replaceRolePermissions(any(), any(), any());
    }

    @Test
    void put_validSet_replacesAtomically_underCriticalAuthorization() {
        Set<CompanyPermission> newSet = Set.of(CompanyPermission.COMPANY_VIEW, CompanyPermission.RFQ_VIEW);

        service.replacePermissions(companyId, actorId, CompanyRole.VIEWER, newSet);

        verify(authorizer).authorize(actorId, companyId, CompanyPermission.ROLE_PERMISSION_MANAGE, null, true);
        verify(permissionRepository).replaceRolePermissions(companyId, CompanyRole.VIEWER, newSet);
    }

    @Test
    void put_emptySetAllowed_revokesEverythingForRole() {
        service.replacePermissions(companyId, actorId, CompanyRole.ACCOUNTANT, Set.of());

        verify(permissionRepository).replaceRolePermissions(eq(companyId), eq(CompanyRole.ACCOUNTANT), any());
    }
}
