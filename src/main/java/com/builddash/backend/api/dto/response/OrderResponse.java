package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        BigDecimal totalAmount,
        String paymentUrl
) {
}
