package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.ApprovalPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalPolicyResponse(
        UUID id,
        UUID companyId,
        BigDecimal amountThreshold,
        List<UUID> categoryIds,
        List<UUID> siteIds,
        List<String> roleStages,
        int escalationHours,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public static ApprovalPolicyResponse from(ApprovalPolicy p) {
        return new ApprovalPolicyResponse(p.id(), p.companyId(), p.amountThreshold(),
                p.categoryIds(), p.siteIds(), p.roleStages().stream().map(Enum::name).toList(),
                p.escalationHours(), p.version(), p.createdAt(), p.updatedAt());
    }
}
