package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnLineItem(
        UUID id,
        UUID returnId,
        UUID productId,
        int quantityRequested,
        BigDecimal refundAmount
) {}
