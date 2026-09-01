package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalEligibilityResolverImplTest {

    @Mock
    private CompanyMemberRepository memberRepository;
    @Mock
    private CompanyRolePermissionRepository rolePermissionRepository;
    @Mock
    private CompanySiteAssignmentRepository siteAssignmentRepository;

    @InjectMocks
    private ApprovalEligibilityResolverImpl resolver;

    private final UUID companyId = UUID.randomUUID();
    private final UUID placerUserId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private CompanyMember member(UUID id, UUID userId, CompanyRole role) {
        return new CompanyMember(id, companyId, userId, role, null, null);
    }

    private void stageHasActRoles(CompanyRole... roles) {
        when(rolePermissionRepository.findRolesWithPermission(companyId, CompanyPermission.APPROVAL_ACT))
                .thenReturn(Set.of(roles));
    }

    @Test
    void eligible_memberWithStageRoleAndAct_isEligible() {
        CompanyMember pm = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.PROCUREMENT_MANAGER);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(pm));
        stageHasActRoles(CompanyRole.PROCUREMENT_MANAGER);
        when(siteAssignmentRepository.findSiteIdsByMemberId(pm.id())).thenReturn(List.of());

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.PROCUREMENT_MANAGER, siteId, placerUserId))
                .containsExactly(pm);
    }

    @Test
    void eligible_ownerImplicitAct_noPermissionRowNeeded() {
        CompanyMember owner = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.OWNER);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(owner));
        stageHasActRoles(); // nothing granted — OWNER still passes
        when(siteAssignmentRepository.findSiteIdsByMemberId(owner.id())).thenReturn(List.of());

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.OWNER, siteId, placerUserId))
                .containsExactly(owner);
    }

    @Test
    void roleInPolicyWithoutAct_isSkipped() {
        CompanyMember pm = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.PROCUREMENT_MANAGER);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(pm));
        stageHasActRoles(); // PROCUREMENT_MANAGER configured in policy but ACT revoked

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.PROCUREMENT_MANAGER, siteId, placerUserId))
                .isEmpty();
        assertThat(resolver.hasEligibleApprover(companyId, CompanyRole.PROCUREMENT_MANAGER, siteId, placerUserId))
                .isFalse();
    }

    @Test
    void memberWithDifferentRole_isSkipped_stageRoleMustMatch() {
        CompanyMember accountant = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.ACCOUNTANT);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(accountant));
        stageHasActRoles(CompanyRole.ACCOUNTANT);

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.PROCUREMENT_MANAGER, siteId, placerUserId))
                .isEmpty();
    }

    @Test
    void placer_isAlwaysExcluded_evenOwnerWithAct() {
        CompanyMember ownerPlacer = member(UUID.randomUUID(), placerUserId, CompanyRole.OWNER);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(ownerPlacer));
        stageHasActRoles();

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.OWNER, siteId, placerUserId)).isEmpty();
    }

    @Test
    void scopedMember_onlyCoversAssignedSites() {
        CompanyMember supervisor = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.SITE_SUPERVISOR);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(supervisor));
        stageHasActRoles(CompanyRole.SITE_SUPERVISOR);
        when(siteAssignmentRepository.findSiteIdsByMemberId(supervisor.id())).thenReturn(List.of(siteId));

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.SITE_SUPERVISOR, siteId, placerUserId))
                .containsExactly(supervisor);
        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.SITE_SUPERVISOR, UUID.randomUUID(), placerUserId))
                .isEmpty();
    }

    @Test
    void nullSiteRequest_allSiteMembersOnly() {
        CompanyMember allSite = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.PROCUREMENT_MANAGER);
        CompanyMember scoped = member(UUID.randomUUID(), UUID.randomUUID(), CompanyRole.PROCUREMENT_MANAGER);
        when(memberRepository.findByCompanyId(companyId)).thenReturn(List.of(allSite, scoped));
        stageHasActRoles(CompanyRole.PROCUREMENT_MANAGER);
        when(siteAssignmentRepository.findSiteIdsByMemberId(allSite.id())).thenReturn(List.of());
        when(siteAssignmentRepository.findSiteIdsByMemberId(scoped.id())).thenReturn(List.of(UUID.randomUUID()));

        assertThat(resolver.eligibleApprovers(companyId, CompanyRole.PROCUREMENT_MANAGER, null, placerUserId))
                .containsExactly(allSite);
    }
}
