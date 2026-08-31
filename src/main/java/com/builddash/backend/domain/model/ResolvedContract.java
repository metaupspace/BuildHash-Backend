package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single contract a pricing pipeline run applies — the winner of the
 * company-contract → user-contract resolution in PricingCalculatorImpl.loadContext.
 * Carries only what PricingSteps.applyContractOverride needs, so the pure step knows
 * nothing about contract tiers, repositories, or precedence.
 */
public record ResolvedContract(
        UUID id,
        BigDecimal unitPrice
) {
}
