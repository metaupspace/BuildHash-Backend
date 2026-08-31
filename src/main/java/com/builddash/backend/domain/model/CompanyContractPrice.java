package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Company-level contract price row — the additive tier above user-level
 * contract_pricing (V7). Same effective-window semantics; overlap is rejected by the
 * excl_company_contract_pricing_no_overlap GiST constraint (V25). Management data
 * enters via seed in v1 (OQ-3); no admin API exists yet.
 */
public record CompanyContractPrice(
        UUID id,
        UUID companyId,
        UUID productId,
        BigDecimal unitPrice,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt
) {
}
