package com.builddash.backend.api.dto.response;

import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Approval request view (9-D). escalationBlocked is derived — PENDING with a null due
 * clock is exactly the ESCALATION_BLOCKED terminal-pending state (locked decision 4).
 */
public record ApprovalResponse(
        UUID id,
        UUID orderId,
        UUID companyId,
        ApprovalRequestStatus status,
        int currentStageIndex,
        String currentRole,
        UUID assignedMemberId,
        Instant escalationDueAt,
        boolean escalationBlocked,
        BigDecimal orderTotalAmount,
        List<String> matchedRules,
        BigDecimal thresholdAmount,
        List<UUID> matchedCategoryIds,
        UUID siteId,
        List<String> roleStages,
        int escalationHours,
        int policyVersion,
        Instant createdAt,
        Instant updatedAt,
        List<Action> actions
) {

    public static ApprovalResponse from(ApprovalRequest r) {
        return from(r, List.of());
    }

    public static ApprovalResponse from(ApprovalService.ApprovalDetail detail) {
        return from(detail.request(), detail.actions());
    }

    public static ApprovalResponse from(ApprovalRequest r, List<ApprovalAction> actions) {
        return new ApprovalResponse(r.id(), r.orderId(), r.companyId(), r.status(),
                r.currentStageIndex(), r.currentRole().name(), r.assignedMemberId(), r.escalationDueAt(),
                r.status() == ApprovalRequestStatus.PENDING && r.escalationDueAt() == null,
                r.orderTotalAmount(), r.matchedRules().stream().map(Enum::name).toList(),
                r.thresholdAmount(), r.matchedCategoryIds(), r.siteId(),
                r.roleStages().stream().map(Enum::name).toList(), r.escalationHours(), r.policyVersion(),
                r.createdAt(), r.updatedAt(),
                actions.stream().map(Action::from).toList());
    }

    public record Action(
            UUID id,
            ApprovalActionType type,
            UUID actorMemberId,
            UUID delegateMemberId,
            int stageIndex,
            String detail,
            Instant createdAt
    ) {

        static Action from(ApprovalAction a) {
            return new Action(a.id(), a.type(), a.actorMemberId(), a.delegateMemberId(),
                    a.stageIndex(), a.detail(), a.createdAt());
        }
    }
}
