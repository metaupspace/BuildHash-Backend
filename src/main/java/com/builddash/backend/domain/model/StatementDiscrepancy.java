package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.StatementDiscrepancyType;

import java.util.UUID;

/** One recorded invoice-readiness discrepancy on a statement (9-E), JSONB-persisted. */
public record StatementDiscrepancy(
        StatementDiscrepancyType type,
        UUID orderId,
        String detail
) {
}
