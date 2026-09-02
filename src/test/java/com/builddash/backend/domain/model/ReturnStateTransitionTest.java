package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.InvalidReturnStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnStateTransitionTest {

    private Return createReturn(ReturnStatus status) {
        UUID returnId = UUID.randomUUID();
        ReturnLineItem item = new ReturnLineItem(
                UUID.randomUUID(),
                returnId,
                UUID.randomUUID(),
                2,
                new BigDecimal("500.00")
        );
        return new Return(
                returnId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                ReturnReason.DAMAGED,
                List.of("returns/photos/photo1.jpg"),
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void approve_fromRequested_succeeds() {
        Return returnObj = createReturn(ReturnStatus.REQUESTED);
        Return approved = returnObj.approve();
        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(approved.id()).isEqualTo(returnObj.id());
        assertThat(approved.orderId()).isEqualTo(returnObj.orderId());
        assertThat(approved.userId()).isEqualTo(returnObj.userId());
        assertThat(approved.photoKeys()).isEqualTo(returnObj.photoKeys());
        assertThat(approved.lineItems()).isEqualTo(returnObj.lineItems());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "APPROVED", "PICKUP_SCHEDULED", "PICKED_UP", "QC", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void approve_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::approve)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void schedulePickup_fromApproved_succeeds() {
        Return returnObj = createReturn(ReturnStatus.APPROVED);
        Return scheduled = returnObj.schedulePickup();
        assertThat(scheduled.status()).isEqualTo(ReturnStatus.PICKUP_SCHEDULED);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "REQUESTED", "PICKUP_SCHEDULED", "PICKED_UP", "QC", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void schedulePickup_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::schedulePickup)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void pickUp_fromPickupScheduled_succeeds() {
        Return returnObj = createReturn(ReturnStatus.PICKUP_SCHEDULED);
        Return pickedUp = returnObj.pickUp();
        assertThat(pickedUp.status()).isEqualTo(ReturnStatus.PICKED_UP);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "REQUESTED", "APPROVED", "PICKED_UP", "QC", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void pickUp_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::pickUp)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void passQc_fromPickedUp_succeeds() {
        Return returnObj = createReturn(ReturnStatus.PICKED_UP);
        Return qcPassed = returnObj.passQc();
        assertThat(qcPassed.status()).isEqualTo(ReturnStatus.QC);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "REQUESTED", "APPROVED", "PICKUP_SCHEDULED", "QC", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void passQc_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::passQc)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void initiateRefund_fromQc_succeeds() {
        Return returnObj = createReturn(ReturnStatus.QC);
        Return refundInitiated = returnObj.initiateRefund();
        assertThat(refundInitiated.status()).isEqualTo(ReturnStatus.REFUND_INITIATED);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "REQUESTED", "APPROVED", "PICKUP_SCHEDULED", "PICKED_UP", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void initiateRefund_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::initiateRefund)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void completeRefund_fromRefundInitiated_succeeds() {
        Return returnObj = createReturn(ReturnStatus.REFUND_INITIATED);
        Return refundCompleted = returnObj.completeRefund();
        assertThat(refundCompleted.status()).isEqualTo(ReturnStatus.REFUND_COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "REQUESTED", "APPROVED", "PICKUP_SCHEDULED", "PICKED_UP", "QC", "REFUND_COMPLETED", "REJECTED"
    })
    void completeRefund_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::completeRefund)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void reject_fromRequested_succeeds() {
        Return returnObj = createReturn(ReturnStatus.REQUESTED);
        Return rejected = returnObj.reject();
        assertThat(rejected.status()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void reject_fromApproved_succeeds() {
        Return returnObj = createReturn(ReturnStatus.APPROVED);
        Return rejected = returnObj.reject();
        assertThat(rejected.status()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void reject_fromPickupScheduled_succeeds() {
        Return returnObj = createReturn(ReturnStatus.PICKUP_SCHEDULED);
        Return rejected = returnObj.reject();
        assertThat(rejected.status()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void reject_fromPickedUp_succeeds() {
        Return returnObj = createReturn(ReturnStatus.PICKED_UP);
        Return rejected = returnObj.reject();
        assertThat(rejected.status()).isEqualTo(ReturnStatus.REJECTED);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {
            "QC", "REFUND_INITIATED", "REFUND_COMPLETED", "REJECTED"
    })
    void reject_fromOtherStates_throwsException(ReturnStatus status) {
        Return returnObj = createReturn(status);
        assertThatThrownBy(returnObj::reject)
                .isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void rejected_isTerminal_noExitTransitions() {
        Return rejected = createReturn(ReturnStatus.REJECTED);
        assertThatThrownBy(rejected::approve).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::schedulePickup).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::pickUp).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::passQc).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::initiateRefund).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::completeRefund).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(rejected::reject).isInstanceOf(InvalidReturnStateException.class);
    }

    @Test
    void refundCompleted_isTerminal_noExitTransitions() {
        Return completed = createReturn(ReturnStatus.REFUND_COMPLETED);
        assertThatThrownBy(completed::approve).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::schedulePickup).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::pickUp).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::passQc).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::initiateRefund).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::completeRefund).isInstanceOf(InvalidReturnStateException.class);
        assertThatThrownBy(completed::reject).isInstanceOf(InvalidReturnStateException.class);
    }
}
