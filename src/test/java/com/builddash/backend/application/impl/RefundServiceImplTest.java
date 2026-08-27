package com.builddash.backend.application.impl;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(returnRepository, paymentRepository, paymentGateway, refundRepository, eventPublisher);
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

    @Test
    void initiateRefund_success_callsGatewaySavesRefundAndTransitionsReturn() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx_gateway_123", new BigDecimal("500.00"), PaymentStatus.SUCCESS, "url");

        when(returnRepository.findById(returnId)).thenReturn(Optional.of(returnObj));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.refund(eq("tx_gateway_123"), eq(new BigDecimal("250.00"))))
                .thenReturn(new RefundReference("ref_gw_999", "PENDING"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        Refund refund = refundService.initiateRefund(returnId);

        assertThat(refund).isNotNull();
        assertThat(refund.returnId()).isEqualTo(returnId);
        assertThat(refund.amount()).isEqualByComparingTo("250.00");
        assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.gatewayRefundId()).isEqualTo("ref_gw_999");
        assertThat(refund.paymentTransactionId()).isEqualTo("tx_gateway_123");

        ArgumentCaptor<Return> returnCaptor = ArgumentCaptor.forClass(Return.class);
        verify(returnRepository).save(returnCaptor.capture());
        assertThat(returnCaptor.getValue().status()).isEqualTo(ReturnStatus.REFUND_INITIATED);
    }

    @Test
    void initiateRefund_returnNotFound_throwsNotFoundException() {
        UUID returnId = UUID.randomUUID();
        when(returnRepository.findById(returnId)).thenReturn(Optional.empty());

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

        when(returnRepository.findById(returnId)).thenReturn(Optional.of(returnObj));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No payment found for order");

        verify(paymentGateway, never()).refund(any(), any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void initiateRefund_gatewayThrowsSynchronously_propagatesExceptionAndRollsBack() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Return returnObj = createQcPassedReturn(returnId, orderId);
        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx_gateway_123", new BigDecimal("500.00"), PaymentStatus.SUCCESS, "url");

        when(returnRepository.findById(returnId)).thenReturn(Optional.of(returnObj));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.refund(any(), any()))
                .thenThrow(new RuntimeException("Simulated gateway connection timeout"));

        assertThatThrownBy(() -> refundService.initiateRefund(returnId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated gateway connection timeout");

        verify(refundRepository, never()).save(any());
        verify(returnRepository, never()).save(any());
    }
}
