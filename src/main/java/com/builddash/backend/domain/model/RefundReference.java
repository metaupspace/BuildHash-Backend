package com.builddash.backend.domain.model;

public record RefundReference(
        String gatewayRefundId,
        String status
) {}
