package com.builddash.backend.application.event;

import com.builddash.backend.domain.enums.CompanyRole;

import java.util.UUID;

/**
 * Published when a gated order's approval request is created (9-D). Carries the
 * resolution inputs for the current stage's approver fan-out — recipients are
 * resolved AFTER_COMMIT against live eligibility, never snapshotted.
 */
public record ApprovalRequestedEvent(
        UUID orderId,
        UUID requestId,
        UUID companyId,
        CompanyRole stageRole,
        UUID siteId,
        UUID placerUserId
) {
}
