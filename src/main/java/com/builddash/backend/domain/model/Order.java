package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.InvalidOrderStateException;

import java.math.BigDecimal;
import java.time.Instant;
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
        UUID deliverySlotLockId,
        Instant placedAt,
        String driverId,
        String driverPhone,
        List<OrderLineItem> lineItems
) {
    public Order confirm() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CONFIRMED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CONFIRMED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order pack() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.PACKED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.PACKED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order dispatch(String driverId, String driverPhone) {
        if (status != OrderStatus.PACKED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.DISPATCHED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.DISPATCHED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order deliver() {
        if (status != OrderStatus.DISPATCHED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.DELIVERED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.DELIVERED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order cancel() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CANCELLED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order cancelConfirmed() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CANCELLED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    /**
     * Warehouse-side cancellation (delivery-partner webhook): CONFIRMED/PACKED/DISPATCHED are all
     * cancellable by the warehouse. No slot-lock interaction — capacity/refund semantics belong to
     * the customer cancel-within-window path (cancelConfirmed) and Phase 6.
     */
    public Order cancelFromDelivery() {
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.PACKED && status != OrderStatus.DISPATCHED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.CANCELLED, deliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order reschedule(UUID newSlotId, LocalDate newSlotDate, UUID newDeliverySlotLockId) {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), "RESCHEDULED");
        }
        return new Order(id, userId, addressId, newSlotId, newSlotDate, totalAmount, status, newDeliverySlotLockId, placedAt, driverId, driverPhone, lineItems);
    }

    public Order updateDriver(String newDriverId, String newDriverPhone) {
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, status, deliverySlotLockId, placedAt,
                newDriverId != null ? newDriverId : driverId,
                newDriverPhone != null ? newDriverPhone : driverPhone,
                lineItems);
    }
}
