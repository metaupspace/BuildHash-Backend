package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.ReturnLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnRefundCalculatorTest {

    @Test
    void calculateItemRefund_fullQuantity_returnsExactLineTotal() {
        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                5,
                new BigDecimal("20.00"),
                new BigDecimal("3.60"),
                new BigDecimal("118.00")
        );

        BigDecimal refund = ReturnRefundCalculator.calculateItemRefund(item, 5);
        assertThat(refund).isEqualByComparingTo("118.00");
    }

    @Test
    void calculateItemRefund_partialQuantity_proportionalHalfUp() {
        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                4,
                new BigDecimal("25.00"),
                new BigDecimal("4.50"),
                new BigDecimal("118.00")
        );

        // 118 * 2 / 4 = 59.00
        BigDecimal refund = ReturnRefundCalculator.calculateItemRefund(item, 2);
        assertThat(refund).isEqualByComparingTo("59.00");
    }

    @Test
    void calculateItemRefund_oddQuantityNonTerminating_roundsHalfUpWithoutArithmeticException() {
        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                new BigDecimal("28.25"),
                new BigDecimal("5.08"),
                new BigDecimal("100.00")
        );

        // 100 * 1 / 3 = 33.3333... -> 33.33
        BigDecimal refund1 = ReturnRefundCalculator.calculateItemRefund(item, 1);
        assertThat(refund1).isEqualByComparingTo("33.33");

        // 100 * 2 / 3 = 66.6666... -> 66.67
        BigDecimal refund2 = ReturnRefundCalculator.calculateItemRefund(item, 2);
        assertThat(refund2).isEqualByComparingTo("66.67");
    }

    @Test
    void calculateItemRefund_invalidInputs_throwIllegalArgumentException() {
        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("50.00"),
                new BigDecimal("9.00"),
                new BigDecimal("109.00")
        );

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund((OrderLineItem) null, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund(item, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund(item, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund(item, 3))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund((BigDecimal) null, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ReturnRefundCalculator.calculateItemRefund(new BigDecimal("100.00"), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculateTotalRefund_sumsAllLineItems() {
        UUID returnId = UUID.randomUUID();
        ReturnLineItem item1 = new ReturnLineItem(UUID.randomUUID(), returnId, UUID.randomUUID(), 1, new BigDecimal("33.33"));
        ReturnLineItem item2 = new ReturnLineItem(UUID.randomUUID(), returnId, UUID.randomUUID(), 2, new BigDecimal("66.67"));

        BigDecimal total = ReturnRefundCalculator.calculateTotalRefund(List.of(item1, item2));
        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    void calculateTotalRefund_emptyOrNull_returnsZero() {
        assertThat(ReturnRefundCalculator.calculateTotalRefund(List.of())).isEqualByComparingTo("0.00");
        assertThat(ReturnRefundCalculator.calculateTotalRefund(null)).isEqualByComparingTo("0.00");
    }
}
