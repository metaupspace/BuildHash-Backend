package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.RefundStatus;

import java.math.BigDecimal;

public record RefundSummaryResponse(
        BigDecimal amount,
        RefundStatus status,
        String gatewayRefundId
) {}
