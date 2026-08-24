package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        BigDecimal totalAmount,
        String paymentUrl,
        Instant placedAt,
        String driverId,
        String driverPhone,
        List<OrderLineItemResponse> lineItems
) {
}
