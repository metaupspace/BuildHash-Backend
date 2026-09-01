package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.InvalidApprovalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One approval request for one gated order (9-D). Born PENDING alongside an order in
 * PENDING_APPROVAL; may remain PENDING indefinitely (no timeout, no auto-cancel).
 *
 * Snapshot fields (orderTotalAmount .. policyVersion) are immutable after creation:
 * later policy edits and catalog price changes never mutate an existing request.
 * Approver eligibility is NOT snapshotted — it resolves against live membership,
 * permissions and site scope at action time.
 */
public record ApprovalRequest(
        UUID id,
        UUID orderId,
        UUID companyId,
        ApprovalRequestStatus status,
        int currentStageIndex,
        CompanyRole currentRole,
        UUID assignedMemberId,
        Instant escalationDueAt,
        BigDecimal orderTotalAmount,
        List<ApprovalMatchRule> matchedRules,
        BigDecimal thresholdAmount,
        List<UUID> matchedCategoryIds,
        UUID siteId,
        List<CompanyRole> roleStages,
        int escalationHours,
        int policyVersion,
        Instant createdAt,
        Instant updatedAt
) {

    public ApprovalRequest {
        matchedRules = matchedRules == null ? List.of() : List.copyOf(matchedRules);
        matchedCategoryIds = matchedCategoryIds == null ? List.of() : List.copyOf(matchedCategoryIds);
        roleStages = roleStages == null ? List.of() : List.copyOf(roleStages);
    }

    public ApprovalRequest approve() {
        return transition(ApprovalRequestStatus.APPROVED);
    }

    public ApprovalRequest reject() {
        return transition(ApprovalRequestStatus.REJECTED);
    }

    public ApprovalRequest cancel() {
        return transition(ApprovalRequestStatus.CANCELLED);
    }

    private ApprovalRequest transition(ApprovalRequestStatus target) {
        requirePending();
        return new ApprovalRequest(id, orderId, companyId, target, currentStageIndex, currentRole,
                assignedMemberId, escalationDueAt, orderTotalAmount, matchedRules, thresholdAmount,
                matchedCategoryIds, siteId, roleStages, escalationHours, policyVersion,
                createdAt, Instant.now());
    }

    /** Single-hop delegation: pins the request to one member; no re-delegation while set. */
    public ApprovalRequest assign(UUID memberId) {
        requirePending();
        if (assignedMemberId != null) {
            throw InvalidApprovalStateException.alreadyDelegated();
        }
        return new ApprovalRequest(id, orderId, companyId, status, currentStageIndex, currentRole,
                memberId, escalationDueAt, orderTotalAmount, matchedRules, thresholdAmount,
                matchedCategoryIds, siteId, roleStages, escalationHours, policyVersion,
                createdAt, Instant.now());
    }

    /**
     * Advance to a later stage. Clears assignedMemberId — delegation is request-stage
     * scoped and does not survive an escalation. Resets the due clock from the
     * snapshotted escalationHours.
     */
    public ApprovalRequest escalateTo(int stageIndex, Instant newDueAt) {
        requirePending();
        if (stageIndex <= currentStageIndex || stageIndex >= roleStages.size()) {
            throw new InvalidApprovalStateException("APPROVAL_STAGE_INVALID",
                    "Cannot escalate to stage " + stageIndex + " from " + currentStageIndex);
        }
        return new ApprovalRequest(id, orderId, companyId, status, stageIndex, roleStages.get(stageIndex),
                null, newDueAt, orderTotalAmount, matchedRules, thresholdAmount,
                matchedCategoryIds, siteId, roleStages, escalationHours, policyVersion,
                createdAt, Instant.now());
    }

    /** Terminal stop for escalation: due clock off, stage unchanged, still PENDING. */
    public ApprovalRequest blockEscalation() {
        requirePending();
        return new ApprovalRequest(id, orderId, companyId, status, currentStageIndex, currentRole,
                assignedMemberId, null, orderTotalAmount, matchedRules, thresholdAmount,
                matchedCategoryIds, siteId, roleStages, escalationHours, policyVersion,
                createdAt, Instant.now());
    }

    private void requirePending() {
        if (status != ApprovalRequestStatus.PENDING) {
            throw InvalidApprovalStateException.notPending(status);
        }
    }
}
