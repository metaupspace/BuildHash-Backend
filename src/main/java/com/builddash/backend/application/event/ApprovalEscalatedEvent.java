package com.builddash.backend.application.event;

import com.builddash.backend.domain.enums.CompanyRole;

import java.util.UUID;

/**
 * Published when escalation advances a PENDING request to a later stage with at
 * least one currently-eligible member (9-D). Recipients resolved AFTER_COMMIT.
 */
public record ApprovalEscalatedEvent(
        UUID orderId,
        UUID requestId,
        UUID companyId,
        int newStageIndex,
        CompanyRole stageRole,
        UUID siteId,
        UUID placerUserId
) {
}
