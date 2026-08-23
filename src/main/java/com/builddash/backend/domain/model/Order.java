package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.InvalidOrderStateException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Order(
        UUID id,
        UUID userId,
        UUID addressId,
        UUID slotId,
        LocalDate slotDate,
        BigDecimal totalAmount,
        OrderStatus status,
        List<OrderLineItem> lineItems
) {
    public Order confirm() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CONFIRMED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CONFIRMED, lineItems);
    }

    public Order cancel() {
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CANCELLED, lineItems);
    }
}
