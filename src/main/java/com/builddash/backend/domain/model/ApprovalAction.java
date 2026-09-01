package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ApprovalActionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit entry for an approval request (9-D). actorMemberId is null for
 * system actions (ESCALATED, ESCALATION_BLOCKED). delegateMemberId is set only on
 * DELEGATED actions. Uniqueness of (requestId, type, stageIndex) is backed by the
 * V29 UNIQUE constraint.
 */
public record ApprovalAction(
        UUID id,
        UUID requestId,
        ApprovalActionType type,
        UUID actorMemberId,
        UUID delegateMemberId,
        int stageIndex,
        String detail,
        Instant createdAt
) {
}
