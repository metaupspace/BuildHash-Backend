package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.service.CompanyPermissionDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private CompanyRolePermissionRepository companyRolePermissionRepository;

    @Mock
    private B2bAuthorizer authorizer;

    @InjectMocks
    private CompanyServiceImpl service;

    private final UUID creator = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Test
    void create_savesCompanyOwnerMembershipAndAllDefaultProfiles_atomically() {
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company created = service.create(creator, "Acme", "27AAAPZ1234C1ZV", "a@b.c", null);

        assertThat(created.businessTimezone()).isEqualTo("Asia/Kolkata"); // default

        var memberCaptor = org.mockito.ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userId()).isEqualTo(creator);
        assertThat(memberCaptor.getValue().role()).isEqualTo(CompanyRole.OWNER);

        // Every customizable role gets its default set; OWNER gets no rows (implicit)
        for (CompanyRole role : CompanyPermissionDefaults.customizableRoles()) {
            verify(companyRolePermissionRepository).replaceRolePermissions(created.id(), role,
                    CompanyPermissionDefaults.forRole(role));
        }
        verify(companyRolePermissionRepository, never()).replaceRolePermissions(
                any(), org.mockito.ArgumentMatchers.eq(CompanyRole.OWNER), any());
    }

    @Test
    void create_permissionInitializationFailure_propagates_rollsBackWholeCreation() {
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("permission init failed"))
                .when(companyRolePermissionRepository).replaceRolePermissions(any(), any(), any());

        // Exception propagates: @Transactional rolls back company + OWNER membership —
        // no partially initialized company can exist.
        assertThatThrownBy(() -> service.create(creator, "Acme", null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("permission init failed");
    }

    @Test
    void get_authorizesWithCompanyView_noLock() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                        CompanyStatus.ACTIVE, null, null)));

        Company result = service.get(companyId, creator);

        verify(authorizer).authorize(creator, companyId, CompanyPermission.COMPANY_VIEW, null, false);
        assertThat(result.id()).isEqualTo(companyId);
    }

    @Test
    void update_authorizesWithCompanyUpdate_critical() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                        CompanyStatus.ACTIVE, null, null)));
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company updated = service.update(companyId, creator, "Renamed", "27G", "x@y.z", "UTC");

        verify(authorizer).authorize(creator, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.businessTimezone()).isEqualTo("UTC");
    }

    @Test
    void updateStatus_companyOwner_withoutAdminRole_forbidden() {
        // H0.4: suspension is a platform action — COMPANY_UPDATE (OWNER implicit)
        // must no longer move the company's own status in either direction.
        assertThatThrownBy(() -> service.updateStatus(companyId, creator, List.of("USER"), CompanyStatus.SUSPENDED))
                .isInstanceOf(ForbiddenException.class);
        verify(companyRepository, never()).save(any());
    }

    @Test
    void updateStatus_appAdmin_suspendsAndReactivates() {
        Company active = new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                CompanyStatus.ACTIVE, null, null);
        when(companyRepository.findByIdForUpdate(companyId)).thenReturn(active);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company suspended = service.updateStatus(companyId, creator, List.of("ADMIN"), CompanyStatus.SUSPENDED);
        assertThat(suspended.status()).isEqualTo(CompanyStatus.SUSPENDED);

        when(companyRepository.findByIdForUpdate(companyId)).thenReturn(suspended);
        Company reactivated = service.updateStatus(companyId, creator, List.of("ADMIN"), CompanyStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(CompanyStatus.ACTIVE);
    }
}
