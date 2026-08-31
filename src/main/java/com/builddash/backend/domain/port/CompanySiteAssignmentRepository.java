package com.builddash.backend.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Site-scope assignments, persisted separately from CompanyMemberRepository: distinct
 * table, distinct lifecycle, and consumers beyond membership management (JWT claim
 * build, scope checks, 9-D approver resolution).
 */
public interface CompanySiteAssignmentRepository {

    /** Replaces the member's site set atomically (delete-all + insert), empty list = unscoped. */
    void replaceForMember(UUID memberId, List<UUID> siteIds);

    List<UUID> findSiteIdsByMemberId(UUID memberId);

    /** Site-scope check (decision E): true when the member is assigned to the site. */
    boolean existsByMemberIdAndSiteId(UUID memberId, UUID siteId);

    List<UUID> findMemberIdsBySiteId(UUID siteId);
}
