package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The B2B approval gate (9-D), invoked from OrderServiceImpl inside the checkout
 * transaction. Two steps so the caller can build the Order row with the right birth
 * status BEFORE inserting it:
 *
 *   evaluate() — pure policy match (OR semantics, null/empty never matches) against
 *                the locked order total, the priced line categories and the site.
 *   openApproval() — joins the caller's transaction (MANDATORY): releases the just-
 *                acquired delivery slot, writes the immutable snapshot, publishes
 *                ApprovalRequestedEvent.
 */
public interface ApprovalGateService {

    GateDecision evaluate(UUID companyId, BigDecimal orderTotal, Collection<UUID> productIds, UUID siteId);

    ApprovalRequest openApproval(Order order, GateDecision decision, UUID deliverySlotLockId);

    /**
     * @param gated             false when no policy row exists or nothing matched
     * @param matchedRules      which conditions matched (OR — can be several)
     * @param matchedCategoryIds the categories that actually matched, snapshot-only
     */
    record GateDecision(
            boolean gated,
            List<ApprovalMatchRule> matchedRules,
            List<UUID> matchedCategoryIds,
            BigDecimal thresholdAmount,
            List<CompanyRole> roleStages,
            int escalationHours,
            int policyVersion
    ) {

        public static GateDecision notGated() {
            return new GateDecision(false, List.of(), List.of(), null, List.of(), 0, 0);
        }
    }
}
