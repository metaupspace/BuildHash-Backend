package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.InvalidReturnStateException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Return(
        UUID id,
        UUID orderId,
        UUID userId,
        ReturnStatus status,
        ReturnReason reason,
        List<String> photoKeys,
        List<ReturnLineItem> lineItems,
        Instant createdAt,
        Instant updatedAt
) {
    public Return approve() {
        if (status != ReturnStatus.REQUESTED) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.APPROVED.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.APPROVED, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return schedulePickup() {
        if (status != ReturnStatus.APPROVED) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.PICKUP_SCHEDULED.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.PICKUP_SCHEDULED, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return pickUp() {
        if (status != ReturnStatus.PICKUP_SCHEDULED) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.PICKED_UP.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.PICKED_UP, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return passQc() {
        if (status != ReturnStatus.PICKED_UP) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.QC.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.QC, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return initiateRefund() {
        if (status != ReturnStatus.QC) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.REFUND_INITIATED.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.REFUND_INITIATED, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return completeRefund() {
        if (status != ReturnStatus.REFUND_INITIATED) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.REFUND_COMPLETED.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.REFUND_COMPLETED, reason, photoKeys, lineItems, createdAt, Instant.now());
    }

    public Return reject() {
        if (status != ReturnStatus.REQUESTED && status != ReturnStatus.APPROVED
                && status != ReturnStatus.PICKUP_SCHEDULED && status != ReturnStatus.PICKED_UP) {
            throw new InvalidReturnStateException(status.name(), ReturnStatus.REJECTED.name());
        }
        return new Return(id, orderId, userId, ReturnStatus.REJECTED, reason, photoKeys, lineItems, createdAt, Instant.now());
    }
}
