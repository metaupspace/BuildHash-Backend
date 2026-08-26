package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID orderId,
        ReturnStatus status,
        ReturnReason reason,
        List<String> photoKeys,
        List<ReturnLineItemResponse> lineItems,
        RefundSummaryResponse refund,
        Instant createdAt,
        Instant updatedAt
) {}
