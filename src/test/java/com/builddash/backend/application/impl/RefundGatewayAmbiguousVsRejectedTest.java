package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.AmbiguousGatewayException;
import com.builddash.backend.domain.exception.GatewayRejectedException;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.Refund;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H1.6: only a definitive gateway rejection may mark a refund claim FAILED (and thus
 * retry-eligible). Every other outcome — including an unclassified RuntimeException —
 * must leave the claim PENDING, since FAILED-then-retry could issue a second real
 * external refund for an operation the gateway may have already completed.
 */
@ExtendWith(MockitoExtension.class)
class RefundGatewayAmbiguousVsRejectedTest {

    @Mock
    private ReturnRepository returnRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private RefundServiceImpl refundService;
    private UUID returnId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        refundService = new RefundServiceImpl(returnRepository, paymentRepository, paymentGateway,
                refundRepository, eventPublisher, transactionTemplate);

        returnId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        Return returnObj = new Return(returnId, orderId, UUID.randomUUID(), ReturnStatus.QC,
                ReturnReason.DAMAGED, List.of("photo.jpg"),
                List.of(new ReturnLineItem(UUID.randomUUID(), returnId, UUID.randomUUID(), 1, new BigDecimal("100.00"))),
                Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnObj));
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of());
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new Payment(UUID.randomUUID(), orderId, "tx_1", new BigDecimal("500.00"), PaymentStatus.SUCCESS, "url")));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void gatewayRejected_marksClaimFailed_allowsRetry() {
        when(paymentGateway.refund(any(), any(), any()))
                .thenThrow(new GatewayRejectedException("Gateway declined"));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(GatewayRejectedException.class);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void ambiguousGatewayException_leavesClaimPending_blocksRetry() {
        when(paymentGateway.refund(any(), any(), any()))
                .thenThrow(new AmbiguousGatewayException("Connection reset"));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(AmbiguousGatewayException.class);

        // Only the initial claim save — never downgraded to FAILED.
        verify(refundRepository, times(1)).save(any(Refund.class));
    }

    @Test
    void unclassifiedRuntimeException_defaultsToAmbiguous_leavesClaimPending() {
        // Fail-safe default: an exception type nobody has classified yet must never be
        // treated as a safe-to-retry rejection.
        when(paymentGateway.refund(any(), any(), any()))
                .thenThrow(new IllegalStateException("Unexpected adapter error"));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(IllegalStateException.class);

        verify(refundRepository, times(1)).save(any(Refund.class));
    }
}
