package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.CompanyMember;

import java.util.List;
import java.util.UUID;

/**
 * Live approver eligibility for a policy stage (9-D). A configured role alone is
 * NEVER sufficient — resolved against current state every call:
 *
 *   current membership ∧ role == stageRole ∧ (current APPROVAL_ACT ∨ OWNER)
 *   ∧ site scope (all-site members always pass; a scoped member only when
 *     request.siteId ∈ their sites; null-site requests are all-site only)
 *   ∧ member is not the order placer.
 *
 * Shared by approve/reject/delegate validation, escalation stage selection and the
 * notification fan-out — one definition, no drift.
 */
public interface ApprovalEligibilityResolver {

    List<CompanyMember> eligibleApprovers(UUID companyId, CompanyRole stageRole, UUID siteId, UUID placerUserId);

    boolean hasEligibleApprover(UUID companyId, CompanyRole stageRole, UUID siteId, UUID placerUserId);
}
