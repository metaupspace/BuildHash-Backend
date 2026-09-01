package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.LastOwnerProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyMembershipServiceImplTest {

    @Mock
    private B2bAuthorizer authorizer;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private CompanySiteRepository companySiteRepository;

    @Mock
    private CompanySiteAssignmentRepository companySiteAssignmentRepository;

    @InjectMocks
    private CompanyMembershipServiceImpl service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID actorMemberId = UUID.randomUUID();

    private CompanyMember actor(CompanyRole role) {
        return new CompanyMember(actorMemberId, companyId, actorId, role, null, null);
    }

    @Test
    void mutations_authorizeWithMemberManage_critical() {
        UUID target = UUID.randomUUID();
        when(companyMemberRepository.findById(target)).thenReturn(Optional.of(
                new CompanyMember(target, companyId, UUID.randomUUID(), CompanyRole.VIEWER, null, null)));

        service.removeMember(companyId, actorId, target);

        verify(authorizer).authorize(actorId, companyId, CompanyPermission.MEMBER_MANAGE, null, true);
    }

    @Test
    void addMember_duplicateConstraintSurfacesAsConflict() {
        when(companyMemberRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.addMember(companyId, actorId, UUID.randomUUID(),
                CompanyRole.VIEWER, List.of()))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void updateMember_selfRoleChange_forbiddenEvenForOwner() {
        // H0.3: nobody changes their own role — an OWNER steps down via transferOwnership.
        when(companyMemberRepository.findById(actorMemberId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));

        assertThatThrownBy(() -> service.updateMember(companyId, actorId, actorMemberId,
                CompanyRole.PROCUREMENT_MANAGER, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_ROLE_CHANGE");
        verify(companyMemberRepository, never()).save(any());
    }

    @Test
    void updateMember_demotingSoleOwnerByAnother_throwsLastOwnerProtected() {
        UUID targetId = UUID.randomUUID();
        CompanyMember soleOwner = new CompanyMember(targetId, companyId,
                UUID.randomUUID(), CompanyRole.OWNER, null, null);
        when(companyMemberRepository.findById(targetId)).thenReturn(Optional.of(soleOwner));
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.PROCUREMENT_MANAGER)));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(soleOwner));

        assertThatThrownBy(() -> service.updateMember(companyId, actorId, targetId,
                CompanyRole.PROCUREMENT_MANAGER, null))
                .isInstanceOf(LastOwnerProtectedException.class);
        verify(companyMemberRepository, never()).save(any());
    }

    @Test
    void updateMember_ownerDemotionWithAnotherOwnerRemaining_succeeds() {
        UUID targetId = UUID.randomUUID();
        CompanyMember target = new CompanyMember(targetId, companyId,
                UUID.randomUUID(), CompanyRole.OWNER, null, null);
        when(companyMemberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(actor(CompanyRole.OWNER), target));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyMember updated = service.updateMember(companyId, actorId, targetId,
                CompanyRole.ACCOUNTANT, null);

        assertThat(updated.role()).isEqualTo(CompanyRole.ACCOUNTANT);
    }

    @Test
    void removeMember_soleOwnerProtected() {
        when(companyMemberRepository.findById(actorMemberId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(actor(CompanyRole.OWNER)));

        assertThatThrownBy(() -> service.removeMember(companyId, actorId, actorMemberId))
                .isInstanceOf(LastOwnerProtectedException.class);
        verify(companyMemberRepository, never()).deleteById(any());
    }

    @Test
    void transferOwnership_nonOwnerActorForbidden_evenWithMemberManageGranted() {
        // MEMBER_MANAGE could be granted to a non-OWNER role by the company's OWNER —
        // the crown still only moves by an OWNER's hand (structural rule).
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.PROCUREMENT_MANAGER)));

        assertThatThrownBy(() -> service.transferOwnership(companyId, actorId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void transferOwnership_oldOwnerBecomesProcurementManager_targetBecomesOwner() {
        UUID targetId = UUID.randomUUID();
        CompanyMember target = new CompanyMember(targetId, companyId, UUID.randomUUID(),
                CompanyRole.SITE_SUPERVISOR, null, null);
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Sole-owner transfer must NOT trip the invariant: actor is still OWNER at check time
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(actor(CompanyRole.OWNER), target));

        service.transferOwnership(companyId, actorId, targetId);

        verify(companyMemberRepository).save(actor(CompanyRole.OWNER)
                .withRole(CompanyRole.PROCUREMENT_MANAGER));
        verify(companyMemberRepository).save(target.withRole(CompanyRole.OWNER));
    }

    @Test
    void updateMember_lockProtocol_memberRowsLockedAfterCompanyRow() {
        // companyRepository lock happens inside the authorizer (critical) — prove the
        // ordering by failing the authorizer's lock and asserting member locks never run
        doAnswer(inv -> {
            companyRepository.findByIdForUpdate(companyId);
            return null;
        }).when(authorizer).authorize(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(true));
        when(companyRepository.findByIdForUpdate(companyId)).thenThrow(new RuntimeException("company-lock"));

        // authorize() throws at the company lock, so member lookup never even runs —
        // proving the protocol's order: company row first, member rows after.
        assertThatThrownBy(() -> service.updateMember(companyId, actorId, UUID.randomUUID(),
                CompanyRole.VIEWER, null))
                .hasMessage("company-lock");
        verify(companyMemberRepository, never()).findById(any());
        verify(companyMemberRepository, never()).findByCompanyIdForUpdate(any());
    }
}
