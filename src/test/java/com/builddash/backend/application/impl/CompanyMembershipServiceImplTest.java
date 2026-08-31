package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.LastAdminProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyMembershipServiceImplTest {

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

    private List<B2bMembership> claim(CompanyRole role) {
        return List.of(new B2bMembership(companyId, role, List.of()));
    }

    @Test
    void addMember_duplicateConstraintSurfacesAsMemberAlreadyExists() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.ADMIN)));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company(
                companyId, "Acme", null, null, "Asia/Kolkata",
                com.builddash.backend.domain.enums.CompanyStatus.ACTIVE, null, null)));
        when(companyMemberRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.addMember(companyId, actorId, claim(CompanyRole.ADMIN),
                UUID.randomUUID(), CompanyRole.BUYER, List.of()))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void updateMember_selfDemotionOfLastOwner_throwsLastAdminProtected() {
        // The only reachable "demote the last OWNER/ADMIN" case: the actor IS the last
        // one (any other admin/owner in the room would satisfy the invariant).
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findById(actorMemberId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(actor(CompanyRole.OWNER)));

        assertThatThrownBy(() -> service.updateMember(companyId, actorId, claim(CompanyRole.OWNER),
                actorMemberId, CompanyRole.BUYER, null))
                .isInstanceOf(LastAdminProtectedException.class);
        verify(companyMemberRepository, never()).save(any());
    }

    @Test
    void updateMember_demotingAdminWhileAnotherAdminExists_succeeds() {
        UUID memberId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.ADMIN)));
        CompanyMember target = new CompanyMember(memberId, companyId, UUID.randomUUID(),
                CompanyRole.ADMIN, null, null);
        when(companyMemberRepository.findById(memberId)).thenReturn(Optional.of(target));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(actor(CompanyRole.ADMIN), target));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companySiteRepository.findByCompanyId(companyId)).thenReturn(List.of(
                new com.builddash.backend.domain.model.CompanySite(siteId, companyId, "HQ",
                        null, true, null, null)));

        CompanyMember updated = service.updateMember(companyId, actorId, claim(CompanyRole.ADMIN),
                memberId, CompanyRole.BUYER, List.of(siteId));

        assertThat(updated.role()).isEqualTo(CompanyRole.BUYER);
        verify(companySiteAssignmentRepository).replaceForMember(org.mockito.ArgumentMatchers.eq(memberId), any());
    }

    @Test
    void updateMember_lockOrder_companyRowBeforeMemberRows() {
        UUID memberId = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.ADMIN)));
        CompanyMember target = new CompanyMember(memberId, companyId, UUID.randomUUID(),
                CompanyRole.OWNER, null, null);
        when(companyMemberRepository.findById(memberId)).thenReturn(Optional.of(target));
        // Step 1 of the protocol throws: proof the company row locks before member rows
        when(companyRepository.findByIdForUpdate(companyId)).thenThrow(new RuntimeException("stop"));

        assertThatThrownBy(() -> service.updateMember(companyId, actorId, claim(CompanyRole.ADMIN),
                memberId, CompanyRole.BUYER, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("stop");
        verify(companyMemberRepository, never()).findByCompanyIdForUpdate(any());
    }

    @Test
    void removeMember_lastAdminProtected() {
        UUID memberId = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findById(memberId))
                .thenReturn(Optional.of(new CompanyMember(memberId, companyId, actorId,
                        CompanyRole.OWNER, null, null)));
        when(companyMemberRepository.findByCompanyIdForUpdate(companyId))
                .thenReturn(List.of(new CompanyMember(memberId, companyId, actorId,
                        CompanyRole.OWNER, null, null)));

        assertThatThrownBy(() -> service.removeMember(companyId, actorId, claim(CompanyRole.OWNER),
                memberId))
                .isInstanceOf(LastAdminProtectedException.class);
        verify(companyMemberRepository, never()).deleteById(any());
    }

    @Test
    void transferOwnership_onlyOwnerMayTransfer() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.ADMIN)));

        assertThatThrownBy(() -> service.transferOwnership(companyId, actorId,
                claim(CompanyRole.ADMIN), UUID.randomUUID()))
                .isInstanceOf(com.builddash.backend.domain.exception.ForbiddenException.class);
    }

    @Test
    void transferOwnership_actorDemotedTargetPromoted_sameProtocol() {
        UUID targetId = UUID.randomUUID();
        CompanyMember target = new CompanyMember(targetId, companyId, UUID.randomUUID(),
                CompanyRole.APPROVER, null, null);
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(actor(CompanyRole.OWNER)));
        when(companyMemberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transferOwnership(companyId, actorId, claim(CompanyRole.OWNER), targetId);

        InOrder order = inOrder(companyRepository, companyMemberRepository);
        order.verify(companyRepository).findByIdForUpdate(companyId);
        order.verify(companyMemberRepository).findByCompanyIdForUpdate(companyId);
        verify(companyMemberRepository).save(actor(CompanyRole.OWNER).withRole(CompanyRole.ADMIN));
        verify(companyMemberRepository).save(target.withRole(CompanyRole.OWNER));
    }
}
