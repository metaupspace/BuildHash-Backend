package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.RefundReference;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 Checkpoint A event-publish proofs for RefundServiceImpl — the QC→REFUND_INITIATED
 * transition lives HERE, not in ReturnServiceImpl.passQc (review-found gap): success fires exactly
 * one ReturnStatusChangedEvent; the payment-not-found path throws before the transition and fires
 * nothing.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceEventPublishTest {

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        // 8.1-C wiring: claim/finalize phases run through the template pass-through.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        refundService = new RefundServiceImpl(returnRepository, paymentRepository, paymentGateway,
                refundRepository, eventPublisher, transactionTemplate);
    }

    private Return qcReturn(UUID returnId, UUID orderId) {
        ReturnLineItem item = new ReturnLineItem(UUID.randomUUID(), returnId, UUID.randomUUID(),
                2, new BigDecimal("250.00"));
        return new Return(returnId, orderId, UUID.randomUUID(), ReturnStatus.QC, ReturnReason.DAMAGED,
                List.of("photo.jpg"), List.of(item), Instant.now(), Instant.now());
    }

    @Test
    void initiateRefund_success_firesQcToRefundInitiated() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(qcReturn(returnId, orderId)));
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of());
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new Payment(UUID.randomUUID(), orderId, "tx_gateway_123", new BigDecimal("500.00"), PaymentStatus.SUCCESS, "url")));
        when(paymentGateway.refund(eq("tx_gateway_123"), eq(new BigDecimal("250.00")), eq(returnId)))
                .thenReturn(new RefundReference("ref_gw_999", "PENDING"));
        java.util.concurrent.atomic.AtomicReference<Refund> lastSaved = new java.util.concurrent.atomic.AtomicReference<>();
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund saved = inv.getArgument(0);
            lastSaved.set(saved);
            return saved;
        });
        when(refundRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(lastSaved.get()));

        refundService.initiateRefund(returnId);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        ReturnStatusChangedEvent event = (ReturnStatusChangedEvent) captor.getValue();
        assertThat(event.returnId()).isEqualTo(returnId);
        assertThat(event.from()).isEqualTo(ReturnStatus.QC);
        assertThat(event.to()).isEqualTo(ReturnStatus.REFUND_INITIATED);
    }

    @Test
    void initiateRefund_paymentNotFound_throwsBeforeTransition_firesNothing() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(qcReturn(returnId, orderId)));
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of());
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.initiateRefund(returnId)).isInstanceOf(NotFoundException.class);

        verify(returnRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
