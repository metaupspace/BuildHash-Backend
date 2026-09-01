package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ApprovalRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository {

    ApprovalRequest save(ApprovalRequest request);

    Optional<ApprovalRequest> findById(UUID id);

    Optional<ApprovalRequest> findByOrderId(UUID orderId);

    /** Pessimistic row lock — always taken AFTER the order row lock (global lock order). */
    Optional<ApprovalRequest> findByIdForUpdate(UUID id);

    /** By order id, pessimistic — placer-cancellation path (after the order row lock). */
    Optional<ApprovalRequest> findByOrderIdForUpdate(UUID orderId);

    /** Due-for-escalation candidates: PENDING with a non-null due timestamp in the past. */
    List<UUID> findDueIds(Instant now);

    /**
     * Company-scoped listing for GET /approvals. Null siteIds = all-site member
     * (company-wide); non-null = site-scoped (snapshot site_id IN siteIds; requests
     * without a site are invisible to site-scoped members).
     */
    List<ApprovalRequest> findByCompanyVisibleInSites(UUID companyId, Collection<UUID> siteIds);
}
