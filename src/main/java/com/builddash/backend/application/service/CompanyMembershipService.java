package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.CompanyMember;

import java.util.List;
import java.util.UUID;

public interface CompanyMembershipService {

    /** MEMBER_MANAGE (critical). Duplicate (company,user) surfaces as 409. */
    CompanyMember addMember(UUID companyId, UUID actorUserId, UUID memberUserId,
                            CompanyRole role, List<UUID> siteIds);

    /** MEMBER_MANAGE (critical); last-owner invariant protected. */
    CompanyMember updateMember(UUID companyId, UUID actorUserId, UUID memberId,
                               CompanyRole role, List<UUID> siteIds);

    /** MEMBER_MANAGE (critical); last-owner invariant protected. */
    void removeMember(UUID companyId, UUID actorUserId, UUID memberId);

    /** OWNER-only (structural, on top of MEMBER_MANAGE); old OWNER becomes PROCUREMENT_MANAGER. */
    void transferOwnership(UUID companyId, UUID actorUserId, UUID targetMemberId);

    /** MEMBER_VIEW. */
    List<CompanyMember> listMembers(UUID companyId, UUID userId);

    /** Site scope of one membership row (empty = unscoped/all sites). */
    List<UUID> siteIdsFor(UUID memberId);
}
