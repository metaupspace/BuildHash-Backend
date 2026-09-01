package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateTransitionTest {

    private Order createOrder(OrderStatus status) {
        return new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                new BigDecimal("150.00"),
                status,
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of()
        );
    }

    @Test
    void confirm_fromPaymentPending_succeeds() {
        Order order = createOrder(OrderStatus.PAYMENT_PENDING);
        Order confirmed = order.confirm();
        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_APPROVAL", "CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void confirm_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::confirm)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void pack_fromConfirmed_succeeds() {
        Order order = createOrder(OrderStatus.CONFIRMED);
        Order packed = order.pack();
        assertThat(packed.status()).isEqualTo(OrderStatus.PACKED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "PENDING_APPROVAL", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void pack_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::pack)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void dispatch_fromPacked_succeeds() {
        Order order = createOrder(OrderStatus.PACKED);
        Order dispatched = order.dispatch("driver-123", "+919876543210");
        assertThat(dispatched.status()).isEqualTo(OrderStatus.DISPATCHED);
        assertThat(dispatched.driverId()).isEqualTo("driver-123");
        assertThat(dispatched.driverPhone()).isEqualTo("+919876543210");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "PENDING_APPROVAL", "CONFIRMED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void dispatch_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(() -> order.dispatch("driver-123", "+919876543210"))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void deliver_fromDispatched_succeeds() {
        Order order = createOrder(OrderStatus.DISPATCHED);
        Order delivered = order.deliver();
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "CONFIRMED", "PACKED", "DELIVERED", "CANCELLED"})
    void deliver_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::deliver)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancel_fromPaymentPending_succeeds() {
        Order order = createOrder(OrderStatus.PAYMENT_PENDING);
        Order cancelled = order.cancel();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_APPROVAL", "CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void cancel_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancelConfirmed_fromConfirmed_succeeds() {
        Order order = createOrder(OrderStatus.CONFIRMED);
        Order cancelled = order.cancelConfirmed();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "PENDING_APPROVAL", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void cancelConfirmed_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::cancelConfirmed)
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void reschedule_fromConfirmed_succeeds() {
        Order order = createOrder(OrderStatus.CONFIRMED);
        UUID newSlotId = UUID.randomUUID();
        LocalDate newDate = LocalDate.now().plusDays(2);
        UUID newLockId = UUID.randomUUID();

        Order rescheduled = order.reschedule(newSlotId, newDate, newLockId);
        assertThat(rescheduled.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(rescheduled.slotId()).isEqualTo(newSlotId);
        assertThat(rescheduled.slotDate()).isEqualTo(newDate);
        assertThat(rescheduled.deliverySlotLockId()).isEqualTo(newLockId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "PENDING_APPROVAL", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void reschedule_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        UUID newSlotId = UUID.randomUUID();
        LocalDate newDate = LocalDate.now().plusDays(2);
        UUID newLockId = UUID.randomUUID();

        assertThatThrownBy(() -> order.reschedule(newSlotId, newDate, newLockId))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---------- 9-D: approval gate transitions ----------

    @Test
    void resumePayment_fromPendingApproval_succeedsAndAssignsNewLock() {
        Order order = createOrder(OrderStatus.PENDING_APPROVAL);
        UUID newLockId = UUID.randomUUID();
        Order resumed = order.resumePayment(newLockId);
        assertThat(resumed.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(resumed.deliverySlotLockId()).isEqualTo(newLockId);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void resumePayment_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(() -> order.resumePayment(UUID.randomUUID()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancelPendingApproval_fromPendingApproval_succeeds() {
        Order order = createOrder(OrderStatus.PENDING_APPROVAL);
        Order cancelled = order.cancelPendingApproval();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"})
    void cancelPendingApproval_fromOtherStates_throwsException(OrderStatus status) {
        Order order = createOrder(status);
        assertThatThrownBy(order::cancelPendingApproval)
                .isInstanceOf(InvalidOrderStateException.class);
    }
}
