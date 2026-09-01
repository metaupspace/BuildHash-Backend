package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ApprovalPolicyValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * A company's approval gate configuration (9-D). Conditions are OR-combined and each
 * is independently optional: null/empty never matches. roleStages is an ordered
 * configuration list — no hierarchy is implied; eligibility additionally requires a
 * live APPROVAL_ACT (OWNER implicitly has it).
 *
 * Immutable per version: PUT replaces the row and increments version; existing
 * ApprovalRequest snapshots are never mutated.
 */
public record ApprovalPolicy(
        UUID id,
        UUID companyId,
        BigDecimal amountThreshold,
        List<UUID> categoryIds,
        List<UUID> siteIds,
        List<CompanyRole> roleStages,
        int escalationHours,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public ApprovalPolicy {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        siteIds = siteIds == null ? List.of() : List.copyOf(siteIds);
        roleStages = roleStages == null ? List.of() : List.copyOf(roleStages);
        if (roleStages.isEmpty()) {
            throw ApprovalPolicyValidationException.emptyRoleStages();
        }
        if (new HashSet<>(roleStages).size() != roleStages.size()) {
            throw ApprovalPolicyValidationException.duplicateRoleStage();
        }
        if (escalationHours < 1) {
            throw ApprovalPolicyValidationException.invalidEscalationHours();
        }
        if (amountThreshold != null && amountThreshold.signum() < 0) {
            throw ApprovalPolicyValidationException.negativeThreshold();
        }
    }

    /** First stage role of a freshly created request. */
    public CompanyRole firstStageRole() {
        return roleStages.get(0);
    }

    /** Copy with version incremented and a fresh timestamp — the PUT replacement. */
    public ApprovalPolicy replaced(BigDecimal newAmountThreshold, List<UUID> newCategoryIds,
                                   List<UUID> newSiteIds, List<CompanyRole> newRoleStages,
                                   int newEscalationHours, Instant now) {
        return new ApprovalPolicy(id, companyId, newAmountThreshold, newCategoryIds, newSiteIds,
                newRoleStages, newEscalationHours, version + 1, createdAt, now);
    }
}
