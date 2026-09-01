package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Company approval-policy management (9-D). PUT is OWNER-only (locked decision 6):
 * no POLICY_MANAGE permission exists — the implicit-ALL OWNER path plus a structural
 * role check, so non-OWNERs are rejected regardless of any permission set they hold.
 */
public interface ApprovalPolicyService {

    /** Read visibility follows COMPANY_VIEW membership; absent policy → 404 (no gate). */
    ApprovalPolicy get(UUID userId, UUID companyId);

    /** Full replacement under the company lock; bumps version. Existing snapshots unchanged. */
    ApprovalPolicy put(UUID userId, UUID companyId, Command command);

    record Command(
            BigDecimal amountThreshold,
            List<UUID> categoryIds,
            List<UUID> siteIds,
            List<CompanyRole> roleStages,
            Integer escalationHours
    ) {
    }
}
