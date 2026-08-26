package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.ReturnLineItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ReturnRefundCalculator {

    public static BigDecimal calculateItemRefund(OrderLineItem orderLineItem, int quantityRequested) {
        if (orderLineItem == null) {
            throw new IllegalArgumentException("OrderLineItem must not be null");
        }
        if (quantityRequested <= 0) {
            throw new IllegalArgumentException("quantityRequested must be greater than 0");
        }
        if (quantityRequested > orderLineItem.quantity()) {
            throw new IllegalArgumentException("quantityRequested (" + quantityRequested + ") cannot exceed ordered quantity (" + orderLineItem.quantity() + ")");
        }
        return calculateItemRefund(orderLineItem.lineTotal(), orderLineItem.quantity(), quantityRequested);
    }

    public static BigDecimal calculateItemRefund(BigDecimal lineTotal, int totalQuantity, int quantityRequested) {
        if (lineTotal == null) {
            throw new IllegalArgumentException("lineTotal must not be null");
        }
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("totalQuantity must be greater than 0");
        }
        if (quantityRequested <= 0) {
            throw new IllegalArgumentException("quantityRequested must be greater than 0");
        }
        if (quantityRequested > totalQuantity) {
            throw new IllegalArgumentException("quantityRequested (" + quantityRequested + ") cannot exceed totalQuantity (" + totalQuantity + ")");
        }

        return lineTotal
                .multiply(BigDecimal.valueOf(quantityRequested))
                .divide(BigDecimal.valueOf(totalQuantity), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateTotalRefund(List<ReturnLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return lineItems.stream()
                .map(ReturnLineItem::refundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
