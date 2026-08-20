package com.builddash.backend.application.service;

import com.builddash.backend.api.dto.response.OrderResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface OrderService {
    OrderResponse create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey);
}
