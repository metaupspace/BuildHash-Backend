package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.LastAdminProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Membership lifecycle. Every mutation is a critical B2B operation (decision 4): the
 * actor's role is re-checked against the current database row, never the token claim.
 *
 * Last-admin invariant protocol (identical lock order everywhere, no advisory locks):
 *   1. lock the company row        — CompanyRepository.findByIdForUpdate
 *   2. lock ALL member rows        — CompanyMemberRepository.findByCompanyIdForUpdate (ORDER BY id)
 *   3. evaluate invariant under lock
 *   4. mutate + commit
 * Concurrent invariant-touching mutations serialize at step 1, so the loser of the
 * race re-reads the post-commit member set and sees the violation instead of
 * interleaving. Transfer-owner runs the same protocol with both writes in one tx.
 */
@Service
@RequiredArgsConstructor
public class CompanyMembershipServiceImpl implements CompanyMembershipService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanySiteRepository companySiteRepository;
    private final CompanySiteAssignmentRepository companySiteAssignmentRepository;

    @Override
    @Transactional
    public CompanyMember addMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                                   UUID memberUserId, CompanyRole role, List<UUID> siteIds) {
        requireAdmin(companyId, actorUserId, callerMemberships);
        requireCompany(companyId);
        validateSitesBelongToCompany(companyId, siteIds);

        CompanyMember member = new CompanyMember(
                UUID.randomUUID(), companyId, memberUserId, role, null, null);
        try {
            CompanyMember saved = companyMemberRepository.save(member);
            companySiteAssignmentRepository.replaceForMember(saved.id(), siteIds);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(company_id, user_id) — either a lost race or a plain duplicate add
            throw new MemberAlreadyExistsException(companyId, memberUserId);
        }
    }

    @Override
    @Transactional
    public CompanyMember updateMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                                      UUID memberId, CompanyRole role, List<UUID> siteIds) {
        CompanyMember target = requireAdmin(companyId, actorUserId, callerMemberships);
        CompanyMember member = companyMemberRepository.findById(memberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + memberId));
        if (siteIds != null) {
            validateSitesBelongToCompany(companyId, siteIds);
        }

        boolean invariantTouching = role != null
                && member.role().atLeast(CompanyRole.ADMIN)
                && !role.atLeast(CompanyRole.ADMIN);

        final CompanyMember mutating = member;
        if (invariantTouching) {
            // Demotion out of OWNER/ADMIN: evaluate the invariant over the POST-change
            // member set (this member's new role no longer counts).
            runInvariantProtocol(companyId, member, currentMembers -> currentMembers.stream()
                    .filter(m -> !m.id().equals(mutating.id()))
                    .anyMatch(m -> m.role().atLeast(CompanyRole.ADMIN)));
        }
        if (role != null) {
            member = companyMemberRepository.save(member.withRole(role));
        }
        if (siteIds != null) {
            companySiteAssignmentRepository.replaceForMember(member.id(), siteIds);
        }
        return member;
    }

    @Override
    @Transactional
    public void removeMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                             UUID memberId) {
        requireAdmin(companyId, actorUserId, callerMemberships);
        CompanyMember member = companyMemberRepository.findById(memberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + memberId));

        if (member.role().atLeast(CompanyRole.ADMIN)) {
            runInvariantProtocol(companyId, member,
                    currentMembers -> currentMembers.stream()
                            .filter(m -> !m.id().equals(member.id()))
                            .anyMatch(m -> m.role().atLeast(CompanyRole.ADMIN)));
        }
        companyMemberRepository.deleteById(member.id());
        // assignments cascade via ON DELETE CASCADE; explicit replace not needed
    }

    @Override
    @Transactional
    public void transferOwnership(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                                  UUID targetMemberId) {
        requireMember(companyId, callerMemberships);
        CompanyMember actor = companyMemberRepository.findByCompanyIdAndUserId(companyId, actorUserId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        if (actor.role() != CompanyRole.OWNER) {
            // Ownership transfer is the one strictly-OWNER operation (the invariant's
            // crown moves only by its holder's hand).
            throw new ForbiddenException("FORBIDDEN", "Only the company OWNER can transfer ownership");
        }
        CompanyMember target = companyMemberRepository.findById(targetMemberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + targetMemberId));

        // Same lock protocol: company row, then all member rows, then both writes in
        // this tx — no commit point exists without an OWNER/ADMIN present.
        runInvariantProtocol(companyId, target, currentMembers -> true);
        companyMemberRepository.save(actor.withRole(CompanyRole.ADMIN));
        companyMemberRepository.save(target.withRole(CompanyRole.OWNER));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyMember> listMembers(UUID companyId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        return companyMemberRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> siteIdsFor(UUID memberId) {
        return companySiteAssignmentRepository.findSiteIdsByMemberId(memberId);
    }

    /**
     * Steps 1-3 of the protocol: locks the company row and the full member set, then
     * evaluates {@code invariantHolds} over the locked state. Throws 422 (rolling the
     * whole tx back, releasing the locks) when the mutation would strand the company.
     */
    private void runInvariantProtocol(UUID companyId, CompanyMember mutatingMember,
                                      java.util.function.Predicate<List<CompanyMember>> invariantHolds) {
        companyRepository.findByIdForUpdate(companyId);
        List<CompanyMember> members = companyMemberRepository.findByCompanyIdForUpdate(companyId);
        if (!invariantHolds.test(members)) {
            throw new LastAdminProtectedException(companyId);
        }
    }

    private void requireMember(UUID companyId, List<B2bMembership> callerMemberships) {
        boolean member = callerMemberships.stream()
                .anyMatch(m -> m.companyId().equals(companyId));
        if (!member) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId);
        }
    }

    /** Critical-operation role re-check: current DB row, not the (possibly stale) claim. */
    private CompanyMember requireAdmin(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        CompanyMember db = companyMemberRepository.findByCompanyIdAndUserId(companyId, actorUserId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        if (!db.role().atLeast(CompanyRole.ADMIN)) {
            throw new ForbiddenException("FORBIDDEN", "Company admin role required");
        }
        return db;
    }

    private void requireCompany(UUID companyId) {
        companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
    }

    private void validateSitesBelongToCompany(UUID companyId, List<UUID> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return;
        }
        List<UUID> companySites = companySiteRepository.findByCompanyId(companyId).stream()
                .map(com.builddash.backend.domain.model.CompanySite::id)
                .toList();
        for (UUID siteId : siteIds) {
            if (!companySites.contains(siteId)) {
                throw new NotFoundException("COMPANY_SITE_NOT_FOUND", "Company site not found: " + siteId);
            }
        }
    }
}
