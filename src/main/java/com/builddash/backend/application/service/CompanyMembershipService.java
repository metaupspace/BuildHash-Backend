package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanyMember;

import java.util.List;
import java.util.UUID;

public interface CompanyMembershipService {

    /** ADMIN+; DB re-checked. Duplicate (company,user) surfaces as the existing 409 style. */
    CompanyMember addMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                            UUID memberUserId, CompanyRole role, List<UUID> siteIds);

    /**
     * ADMIN+; DB re-checked. Demotions/removals that would strand the company without
     * an OWNER/ADMIN run the pessimistic-lock invariant protocol and throw
     * LastAdminProtectedException (422).
     */
    CompanyMember updateMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                               UUID memberId, CompanyRole role, List<UUID> siteIds);

    /** ADMIN+; DB re-checked; same invariant protocol. */
    void removeMember(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships, UUID memberId);

    /** OWNER-only; DB re-checked; old OWNER becomes ADMIN, target becomes OWNER, one transaction. */
    void transferOwnership(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships, UUID targetMemberId);

    /** Member-only read (any company role). */
    List<CompanyMember> listMembers(UUID companyId, List<B2bMembership> callerMemberships);

    /** Site scope of one membership row (empty = unscoped/all sites). */
    List<UUID> siteIdsFor(UUID memberId);
}
