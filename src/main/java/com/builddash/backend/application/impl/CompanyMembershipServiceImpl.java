package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.LastOwnerProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.CompanySite;
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
 * Membership lifecycle. All mutations are critical: B2bAuthorizer takes the
 * company-row lock, then membership/permission state is resolved from the database —
 * never the JWT.
 *
 * Last-OWNER invariant protocol (deterministic lock order, no advisory locks):
 *   1. B2bAuthorizer (critical) locks the company row
 *   2. findByCompanyIdForUpdate locks ALL member rows (ORDER BY id)
 *   3. invariant "at least one OWNER remains" evaluated under lock
 *   4. mutate + commit
 *
 * Ownership transfer: any OWNER may transfer; the old OWNER becomes
 * PROCUREMENT_MANAGER and the target becomes OWNER in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class CompanyMembershipServiceImpl implements CompanyMembershipService {

    private final B2bAuthorizer authorizer;
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanySiteRepository companySiteRepository;
    private final CompanySiteAssignmentRepository companySiteAssignmentRepository;

    @Override
    @Transactional
    public CompanyMember addMember(UUID companyId, UUID actorUserId, UUID memberUserId,
                                   CompanyRole role, List<UUID> siteIds) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.MEMBER_MANAGE, null, true);
        if (role == CompanyRole.OWNER) {
            requireOwnerCrownAuthority(companyId, actorUserId);
        }
        validateSitesBelongToCompany(companyId, siteIds);

        CompanyMember member = new CompanyMember(
                UUID.randomUUID(), companyId, memberUserId, role, null, null);
        try {
            CompanyMember saved = companyMemberRepository.save(member);
            companySiteAssignmentRepository.replaceForMember(saved.id(), siteIds);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(company_id, user_id): a lost race or a plain duplicate add
            throw new MemberAlreadyExistsException(companyId, memberUserId);
        }
    }

    @Override
    @Transactional
    public CompanyMember updateMember(UUID companyId, UUID actorUserId, UUID memberId,
                                      CompanyRole role, List<UUID> siteIds) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.MEMBER_MANAGE, null, true);
        CompanyMember member = companyMemberRepository.findById(memberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + memberId));
        if (role != null) {
            CompanyMember actor = requireActorMember(companyId, actorUserId);
            boolean selfChange = member.id().equals(actor.id()) && role != member.role();
            if (selfChange) {
                throw new ForbiddenException("SELF_ROLE_CHANGE",
                        "Members cannot change their own role");
            }
            if (role == CompanyRole.OWNER) {
                requireOwnerCrownAuthority(companyId, actorUserId);
            }
        }
        if (siteIds != null) {
            validateSitesBelongToCompany(companyId, siteIds);
        }

        boolean demotesOwner = member.role() == CompanyRole.OWNER
                && role != null && role != CompanyRole.OWNER;
        if (demotesOwner) {
            runOwnerInvariantProtocol(companyId, member);
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
    public void removeMember(UUID companyId, UUID actorUserId, UUID memberId) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.MEMBER_MANAGE, null, true);
        CompanyMember member = companyMemberRepository.findById(memberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + memberId));

        if (member.role() == CompanyRole.OWNER) {
            runOwnerInvariantProtocol(companyId, member);
        }
        companyMemberRepository.deleteById(member.id());
        // assignments cascade via ON DELETE CASCADE
    }

    @Override
    @Transactional
    public void transferOwnership(UUID companyId, UUID actorUserId, UUID targetMemberId) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.MEMBER_MANAGE, null, true);
        // Structural rule on top of the permission: the ownership crown moves only by
        // an OWNER's hand — MEMBER_MANAGE may be granted to other roles for everyday
        // member administration, but never for transfer.
        CompanyMember actor = requireActorMember(companyId, actorUserId);
        if (actor.role() != CompanyRole.OWNER) {
            throw new ForbiddenException("FORBIDDEN", "Only a company OWNER can transfer ownership");
        }
        CompanyMember target = companyMemberRepository.findById(targetMemberId)
                .filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found: " + targetMemberId));

        // Same protocol: no commit point without an OWNER present
        runOwnerInvariantProtocol(companyId, target);
        companyMemberRepository.save(actor.withRole(CompanyRole.PROCUREMENT_MANAGER));
        companyMemberRepository.save(target.withRole(CompanyRole.OWNER));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyMember> listMembers(UUID companyId, UUID userId) {
        authorizer.authorize(userId, companyId, CompanyPermission.MEMBER_VIEW, null, false);
        return companyMemberRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> siteIdsFor(UUID memberId) {
        return companySiteAssignmentRepository.findSiteIdsByMemberId(memberId);
    }

    /**
     * Locks all member rows (company row already locked by the authorizer) and
     * verifies at least one OTHER OWNER remains after the mutation. Throws 422 —
     * rolling the whole transaction back — when the company would be stranded.
     *
     * transferOwnership also routes through here for the lock: at check time the
     * transferring actor is still an OWNER row, so a transfer can never trip the
     * invariant — only demotion/removal can.
     */
    private void runOwnerInvariantProtocol(UUID companyId, CompanyMember mutatingOwner) {
        List<CompanyMember> members = companyMemberRepository.findByCompanyIdForUpdate(companyId);
        boolean anotherOwnerRemains = members.stream()
                .filter(m -> !m.id().equals(mutatingOwner.id()))
                .anyMatch(m -> m.role() == CompanyRole.OWNER);
        if (!anotherOwnerRemains) {
            throw new LastOwnerProtectedException(companyId);
        }
    }

    private CompanyMember requireActorMember(UUID companyId, UUID actorUserId) {
        return companyMemberRepository.findByCompanyIdAndUserId(companyId, actorUserId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
    }

    /**
     * H0.3: the ownership crown moves only by an OWNER's hand — the same structural
     * rule transferOwnership carries, applied to the two sibling entry points that
     * can also assign OWNER (addMember, updateMember). The actor's role is read from
     * the invariant-protocol all-member lock, so a concurrent demotion of the actor
     * cannot interleave with a crown assignment.
     */
    private void requireOwnerCrownAuthority(UUID companyId, UUID actorUserId) {
        List<CompanyMember> members = companyMemberRepository.findByCompanyIdForUpdate(companyId);
        CompanyMember actor = members.stream()
                .filter(m -> m.userId().equals(actorUserId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        if (actor.role() != CompanyRole.OWNER) {
            throw new ForbiddenException("FORBIDDEN", "Only a company OWNER can assign the OWNER role");
        }
    }

    private void validateSitesBelongToCompany(UUID companyId, List<UUID> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return;
        }
        List<UUID> companySites = companySiteRepository.findByCompanyId(companyId).stream()
                .map(CompanySite::id)
                .toList();
        for (UUID siteId : siteIds) {
            if (!companySites.contains(siteId)) {
                throw new NotFoundException("COMPANY_SITE_NOT_FOUND", "Company site not found: " + siteId);
            }
        }
    }
}
