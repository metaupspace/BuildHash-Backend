package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnLineItemResponse(
        UUID id,
        UUID productId,
        int quantityRequested,
        BigDecimal refundAmount
) {}
