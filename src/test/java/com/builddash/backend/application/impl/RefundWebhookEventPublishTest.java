package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 Checkpoint A event-publish proofs for the REAL refund success path (not the
 * dummy-gateway RefundWebhookEvent): success fires exactly two events in order
 * (ReturnStatusChanged REFUND_INITIATED→REFUND_COMPLETED, then RefundCompleted); FAILED, unknown
 * status, and already-terminal refunds fire nothing.
 */
@ExtendWith(MockitoExtension.class)
class RefundWebhookEventPublishTest {

    private static final String SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private GstNoteRepository gstNoteRepository;

    @Mock
    private GstSequenceService gstSequenceService;

    @Mock
    private PaymentWebhookConfig webhookConfig;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RefundWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new RefundWebhookServiceImpl(returnRepository, org.mockito.Mockito.mock(com.builddash.backend.application.service.ApplicationMetrics.class), refundRepository,
                gstNoteRepository, gstSequenceService, webhookConfig, eventPublisher);
        lenient().when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
    }

    private static String sign(UUID returnId, String gatewayRefundId, String status) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = returnId + ":" + gatewayRefundId + ":" + status;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Refund pendingRefund(UUID refundId, UUID returnId, String gatewayRefundId, RefundStatus status) {
        return new Refund(refundId, returnId, "tx_1", new BigDecimal("250.00"), status,
                gatewayRefundId, Instant.now(), Instant.now());
    }

    private Return returnIn(UUID returnId, ReturnStatus status) {
        return new Return(returnId, UUID.randomUUID(), UUID.randomUUID(), status, ReturnReason.DAMAGED,
                List.of("photo.jpg"),
                List.of(new ReturnLineItem(UUID.randomUUID(), returnId, UUID.randomUUID(), 2, new BigDecimal("250.00"))),
                Instant.now(), Instant.now());
    }

    @Test
    void success_firesReturnStatusChangedThenRefundCompleted() {
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Refund refund = pendingRefund(refundId, returnId, "ref_gw_1", RefundStatus.PENDING);
        when(refundRepository.findByGatewayRefundId("ref_gw_1")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnIn(returnId, ReturnStatus.REFUND_INITIATED)));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));
        lenient().when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(gstNoteRepository.findByReturnId(returnId)).thenReturn(Optional.empty());
        lenient().when(gstSequenceService.nextNumber(GstSequenceType.CREDIT_NOTE)).thenReturn("CN-000042");
        lenient().when(gstNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookService.handleWebhook(returnId, "ref_gw_1", "SUCCESS", sign(returnId, "ref_gw_1", "SUCCESS"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        ReturnStatusChangedEvent statusEvent = (ReturnStatusChangedEvent) captor.getAllValues().get(0);
        assertThat(statusEvent.returnId()).isEqualTo(returnId);
        assertThat(statusEvent.from()).isEqualTo(ReturnStatus.REFUND_INITIATED);
        assertThat(statusEvent.to()).isEqualTo(ReturnStatus.REFUND_COMPLETED);
        RefundCompletedEvent completedEvent = (RefundCompletedEvent) captor.getAllValues().get(1);
        assertThat(completedEvent.returnId()).isEqualTo(returnId);
        assertThat(completedEvent.refundId()).isEqualTo(refundId);
    }

    @Test
    void failedStatus_firesNothing() {
        UUID returnId = UUID.randomUUID();
        Refund refund = pendingRefund(UUID.randomUUID(), returnId, "ref_gw_1", RefundStatus.PENDING);
        when(refundRepository.findByGatewayRefundId("ref_gw_1")).thenReturn(Optional.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnIn(returnId, ReturnStatus.QC)));
        when(refundRepository.findByIdForUpdate(refund.id())).thenReturn(Optional.of(refund));

        webhookService.handleWebhook(returnId, "ref_gw_1", "FAILED", sign(returnId, "ref_gw_1", "FAILED"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unknownStatus_firesNothing() {
        UUID returnId = UUID.randomUUID();
        Refund refund = pendingRefund(UUID.randomUUID(), returnId, "ref_gw_1", RefundStatus.PENDING);
        when(refundRepository.findByGatewayRefundId("ref_gw_1")).thenReturn(Optional.of(refund));
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnIn(returnId, ReturnStatus.QC)));
        when(refundRepository.findByIdForUpdate(refund.id())).thenReturn(Optional.of(refund));

        webhookService.handleWebhook(returnId, "ref_gw_1", "WEIRD", sign(returnId, "ref_gw_1", "WEIRD"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void alreadyTerminalRefund_firesNothing() {
        UUID returnId = UUID.randomUUID();
        Refund refund = pendingRefund(UUID.randomUUID(), returnId, "ref_gw_1", RefundStatus.SUCCESS);
        when(refundRepository.findByGatewayRefundId("ref_gw_1")).thenReturn(Optional.of(refund));
        when(returnRepository.findByIdForUpdate(returnId)).thenReturn(Optional.of(returnIn(returnId, ReturnStatus.REFUND_COMPLETED)));
        when(refundRepository.findByIdForUpdate(refund.id())).thenReturn(Optional.of(refund));

        webhookService.handleWebhook(returnId, "ref_gw_1", "SUCCESS", sign(returnId, "ref_gw_1", "SUCCESS"));

        verify(refundRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
