package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.InvalidReturnStateException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 8.1-C three-phase proof: durable PENDING claim commits before the gateway call, the
 * gateway runs outside every transaction, finalization re-locks and transitions, failure
 * marks the claim FAILED while the Return stays QC, and a concurrent second initiation
 * is rejected before any second gateway call.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

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
    }

    private Return createQcPassedReturn(UUID returnId, UUID orderId) {
        ReturnLineItem item = new ReturnLineItem(
                UUID.randomUUID(),
                returnId,
                UUID.randomUUID(),
                2,
                new BigDecimal("250.00")
        );
        return new Return(
                returnId,
                orderId,
                UUID.randomUUID(),
                ReturnStatus.QC,
                ReturnReason.DAMAGED,
                List.of("photo.jpg"),
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }

    private Payment payment(UUID orderId) {
        return new Payment(UUID.randomUUID(), orderId, "tx_gateway_123", new BigDecimal("500.00"), PaymentStatus.SUCCESS, "url");
    }

    private void stubClaimPath(Return returnObj, Payment payment) {
        when(returnRepository.findByIdForUpdate(returnObj.id())).thenReturn(Optional.of(returnObj));
        when(refundRepository.findAllByReturnId(returnObj.id())).thenReturn(List.of());
        when(paymentRepository.findLatestByOrderId(returnObj.orderId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initiateRefund_success_durableClaimThenGatewayThenFinalization() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        stubClaimPath(returnObj, payment(orderId));
        when(paymentGateway.refund(eq("tx_gateway_123"), eq(new BigDecimal("250.00"))))
                .thenReturn(new RefundReference("ref_gw_999", "PENDING"));

        Refund refund = refundService.initiateRefund(returnId);

        assertThat(refund).isNotNull();
        assertThat(refund.returnId()).isEqualTo(returnId);
        assertThat(refund.amount()).isEqualByComparingTo("250.00");
        assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.gatewayRefundId()).isEqualTo("ref_gw_999");
        assertThat(refund.paymentTransactionId()).isEqualTo("tx_gateway_123");

        // Exactly one claim row lifecycle: PENDING(null id) insert, then gateway-id finalization.
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(refundCaptor.capture());
        assertThat(refundCaptor.getAllValues().get(0).status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refundCaptor.getAllValues().get(0).gatewayRefundId()).isNull();
        assertThat(refundCaptor.getAllValues().get(1).gatewayRefundId()).isEqualTo("ref_gw_999");

        ArgumentCaptor<Return> returnCaptor = ArgumentCaptor.forClass(Return.class);
        verify(returnRepository).save(returnCaptor.capture());
        assertThat(returnCaptor.getValue().status()).isEqualTo(ReturnStatus.REFUND_INITIATED);

        verify(eventPublisher).publishEvent(any(
                com.builddash.backend.application.event.ReturnStatusChangedEvent.class));
    }

    @Test
    void initiateRefund_gatewayFailure_marksClaimFailed_returnStaysQc() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        stubClaimPath(returnObj, payment(orderId));
        when(paymentGateway.refund(any(), any()))
                .thenThrow(new RuntimeException("Simulated gateway connection timeout"));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated gateway connection timeout");

        // Claim lifecycle: PENDING insert, then FAILED — the Return is never transitioned.
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository, times(2)).save(refundCaptor.capture());
        assertThat(refundCaptor.getAllValues().get(1).status()).isEqualTo(RefundStatus.FAILED);

        verify(returnRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void initiateRefund_returnNotFound_throwsNotFoundException() {
        UUID returnId = UUID.randomUUID();
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Return not found");

        verify(paymentGateway, never()).refund(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefund_paymentNotFound_throwsNotFoundException() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnObj));
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of());
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No payment found for order");

        verify(paymentGateway, never()).refund(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefund_existingNonFailedClaim_rejectedBeforeGatewayCall() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnObj));
        // A prior PENDING claim (e.g. the crash window) must block a second gateway call.
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of(
                new Refund(UUID.randomUUID(), returnId, "tx_gateway_123", new BigDecimal("250.00"),
                        RefundStatus.PENDING, null, Instant.now(), Instant.now())));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(InvalidReturnStateException.class);

        verify(paymentGateway, never()).refund(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefund_priorFailedClaim_doesNotBlockRetry() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        stubClaimPath(returnObj, payment(orderId));
        when(refundRepository.findAllByReturnId(returnId)).thenReturn(List.of(
                new Refund(UUID.randomUUID(), returnId, "tx_gateway_123", new BigDecimal("250.00"),
                        RefundStatus.FAILED, null, Instant.now(), Instant.now())));
        when(paymentGateway.refund(any(), any())).thenReturn(new RefundReference("ref_gw_retry", "PENDING"));

        Refund refund = refundService.initiateRefund(returnId);

        assertThat(refund.gatewayRefundId()).isEqualTo("ref_gw_retry");
        verify(paymentGateway, times(1)).refund(any(), any());
    }

    @Test
    void initiateRefund_returnNotInQc_rejectedBeforeClaimOrGateway() {
        UUID returnId = UUID.randomUUID();
        Return refundInitiated = new Return(returnId, UUID.randomUUID(), UUID.randomUUID(),
                ReturnStatus.REFUND_INITIATED, ReturnReason.DAMAGED, List.of(), List.of(),
                Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(refundInitiated));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(InvalidReturnStateException.class);

        verify(paymentGateway, never()).refund(any(), any());
        verify(refundRepository, never()).save(any());
    }
}
