package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.InvalidOrderStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * companyId/siteId are B2B tagging (V25): null for every B2C order, set at creation
 * for B2B orders (site population arrives with 9-B/9-C association flows).
 *
 * confirmedAt is stamped ONLY by confirm() — the payment-webhook path. Creation never
 * sets it; it is the statement reporting timestamp (9-E). No transition semantics
 * changed in 9-A: same states, same guards.
 */
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
        List<OrderLineItem> lineItems,
        UUID companyId,
        UUID siteId,
        Instant confirmedAt
) {

    public Order {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    /** Compatibility constructor preserving the pre-9A call shape (no B2B tagging). */
    public Order(UUID id, UUID userId, UUID addressId, UUID slotId, LocalDate slotDate,
                 BigDecimal totalAmount, OrderStatus status, UUID deliverySlotLockId,
                 Instant placedAt, String driverId, String driverPhone, List<OrderLineItem> lineItems) {
        this(id, userId, addressId, slotId, slotDate, totalAmount, status, deliverySlotLockId,
                placedAt, driverId, driverPhone, lineItems, null, null, null);
    }

    private Order with(OrderStatus next, Instant confirmedAt) {
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, next,
                deliverySlotLockId, placedAt, driverId, driverPhone, lineItems,
                companyId, siteId, confirmedAt != null ? confirmedAt : this.confirmedAt);
    }

    public Order confirm() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CONFIRMED.name());
        }
        return with(OrderStatus.CONFIRMED, Instant.now());
    }

    public Order pack() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.PACKED.name());
        }
        return with(OrderStatus.PACKED, null);
    }

    public Order dispatch(String driverId, String driverPhone) {
        if (status != OrderStatus.PACKED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.DISPATCHED.name());
        }
        return with(OrderStatus.DISPATCHED, null)
                .updateDriver(driverId, driverPhone);
    }

    public Order deliver() {
        if (status != OrderStatus.DISPATCHED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.DELIVERED.name());
        }
        return with(OrderStatus.DELIVERED, null);
    }

    public Order cancel() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return with(OrderStatus.CANCELLED, null);
    }

    /**
     * 9-D approval gate: approval won and the delivery slot was re-acquired, so the
     * order re-enters the ordinary payment flow (same state the webhook/confirm path
     * expects). No confirmedAt — that stays webhook-only.
     */
    public Order resumePayment(UUID newDeliverySlotLockId) {
        if (status != OrderStatus.PENDING_APPROVAL) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.PAYMENT_PENDING.name());
        }
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, OrderStatus.PAYMENT_PENDING,
                newDeliverySlotLockId, placedAt, driverId, driverPhone, lineItems,
                companyId, siteId, confirmedAt);
    }

    /**
     * 9-D: cancellation while gated (placer cancel, rejection, slot reacquisition
     * failure). No slot release — the gate released capacity at creation.
     */
    public Order cancelPendingApproval() {
        if (status != OrderStatus.PENDING_APPROVAL) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return with(OrderStatus.CANCELLED, null);
    }

    public Order cancelConfirmed() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), OrderStatus.CANCELLED.name());
        }
        return with(OrderStatus.CANCELLED, null);
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
        return with(OrderStatus.CANCELLED, null);
    }

    public Order reschedule(UUID newSlotId, LocalDate newSlotDate, UUID newDeliverySlotLockId) {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(status.name(), "RESCHEDULED");
        }
        return new Order(id, userId, addressId, newSlotId, newSlotDate, totalAmount, status,
                newDeliverySlotLockId, placedAt, driverId, driverPhone, lineItems,
                companyId, siteId, confirmedAt);
    }

    public Order updateDriver(String newDriverId, String newDriverPhone) {
        return new Order(id, userId, addressId, slotId, slotDate, totalAmount, status, deliverySlotLockId, placedAt,
                newDriverId != null ? newDriverId : driverId,
                newDriverPhone != null ? newDriverPhone : driverPhone,
                lineItems, companyId, siteId, confirmedAt);
    }
}
