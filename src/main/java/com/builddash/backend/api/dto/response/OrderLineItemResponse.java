package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineItemResponse(
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal taxAmount
) {
}
