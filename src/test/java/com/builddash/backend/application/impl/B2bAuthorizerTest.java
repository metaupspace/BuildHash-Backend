package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class B2bAuthorizerTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private CompanyRolePermissionRepository companyRolePermissionRepository;

    @Mock
    private CompanySiteAssignmentRepository companySiteAssignmentRepository;

    @InjectMocks
    private B2bAuthorizerImpl authorizer;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private CompanyMember member(CompanyRole role) {
        return new CompanyMember(memberId, companyId, userId, role, null, null);
    }

    @Test
    void nonMember_gets404_evenBeforePermissionLookup() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.authorize(userId, companyId, CompanyPermission.COMPANY_VIEW, null, false))
                .isInstanceOf(NotFoundException.class);
        verify(companyRolePermissionRepository, never()).findPermissions(companyId, CompanyRole.OWNER);
    }

    @Test
    void owner_implicitAll_neverReadsPermissionRows() {
        when(companyRepository.findByIdForUpdate(companyId)).thenReturn(activeCompany());
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(member(CompanyRole.OWNER)));

        assertThatCode(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.ROLE_PERMISSION_MANAGE, null, true))
                .doesNotThrowAnyException();
        verify(companyRolePermissionRepository, never())
                .findPermissions(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(companyRepository).findByIdForUpdate(companyId); // critical lock taken
    }

    @Test
    void critical_suspendedCompany_forbiddenBeforeMembershipLookup() {
        when(companyRepository.findByIdForUpdate(companyId))
                .thenReturn(new com.builddash.backend.domain.model.Company(companyId, "Co", null, null,
                        "Asia/Kolkata", com.builddash.backend.domain.enums.CompanyStatus.SUSPENDED, null, null));

        assertThatThrownBy(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.ORDER_CREATE, null, true))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
        verify(companyMemberRepository, never()).findByCompanyIdAndUserId(companyId, userId);
    }

    private com.builddash.backend.domain.model.Company activeCompany() {
        return new com.builddash.backend.domain.model.Company(companyId, "Co", null, null,
                "Asia/Kolkata", com.builddash.backend.domain.enums.CompanyStatus.ACTIVE, null, null);
    }

    @Test
    void grantedPermission_passes_missingPermission_gets403() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(member(CompanyRole.VIEWER)));
        when(companyRolePermissionRepository.findPermissions(companyId, CompanyRole.VIEWER))
                .thenReturn(Set.of(CompanyPermission.COMPANY_VIEW));

        assertThatCode(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.COMPANY_VIEW, null, false)).doesNotThrowAnyException();

        assertThatThrownBy(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.COMPANY_UPDATE, null, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void readSiteMismatch_404_mutationSiteMismatch_403() {
        when(companyRepository.findByIdForUpdate(companyId)).thenReturn(activeCompany());
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(member(CompanyRole.SITE_SUPERVISOR)));
        when(companyRolePermissionRepository.findPermissions(companyId, CompanyRole.SITE_SUPERVISOR))
                .thenReturn(Set.of(CompanyPermission.SITE_VIEW, CompanyPermission.ORDER_VIEW));
        when(companySiteAssignmentRepository.findSiteIdsByMemberId(memberId))
                .thenReturn(List.of(UUID.randomUUID())); // assigned elsewhere, not siteId

        assertThatThrownBy(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.ORDER_VIEW, siteId, false))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.ORDER_VIEW, siteId, true))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unscopedMembership_coversEverySite() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(member(CompanyRole.VIEWER)));
        when(companyRolePermissionRepository.findPermissions(companyId, CompanyRole.VIEWER))
                .thenReturn(Set.of(CompanyPermission.ORDER_VIEW));
        when(companySiteAssignmentRepository.findSiteIdsByMemberId(memberId))
                .thenReturn(List.of()); // empty = all sites

        assertThatCode(() -> authorizer.authorize(userId, companyId,
                CompanyPermission.ORDER_VIEW, siteId, false)).doesNotThrowAnyException();
    }

    @Test
    void read_neverTakesCompanyRowLock() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(member(CompanyRole.OWNER)));

        authorizer.authorize(userId, companyId, CompanyPermission.COMPANY_VIEW, null, false);

        verify(companyRepository, never()).findByIdForUpdate(companyId);
    }
}
