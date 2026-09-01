package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.CompanyRole;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Full-replacement PUT body. Every condition is independently optional; roleStages is
 * the ordered escalation sequence (configuration only — no hierarchy implied).
 */
public record ApprovalPolicyRequest(
        BigDecimal amountThreshold,
        List<UUID> categoryIds,
        List<UUID> siteIds,
        @NotEmpty List<CompanyRole> roleStages,
        Integer escalationHours
) {
}
