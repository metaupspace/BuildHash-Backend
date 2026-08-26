package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
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
import org.springframework.dao.DataIntegrityViolationException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundWebhookServiceImplTest {

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

    private RefundWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new RefundWebhookServiceImpl(
                returnRepository,
                refundRepository,
                gstNoteRepository,
                gstSequenceService,
                webhookConfig
        );
    }

    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Return createReturn(UUID returnId, ReturnStatus status) {
        return new Return(
                returnId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                ReturnReason.DAMAGED,
                List.of("photo.jpg"),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private Refund createRefund(UUID returnId, String gatewayRefundId, RefundStatus status) {
        return new Refund(
                UUID.randomUUID(),
                returnId,
                "tx_123",
                new BigDecimal("200.00"),
                status,
                gatewayRefundId,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void handleWebhook_success_updatesRefundAndTransitionsReturn() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_123";
        String status = "SUCCESS";
        String signature = sign(returnId + ":" + gatewayRefundId + ":" + status);

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
        Refund refund = createRefund(returnId, gatewayRefundId, RefundStatus.PENDING);
        Return returnObj = createReturn(returnId, ReturnStatus.REFUND_INITIATED);

        when(refundRepository.findByGatewayRefundId(gatewayRefundId)).thenReturn(Optional.of(refund));
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(returnObj));
        when(gstNoteRepository.findByReturnId(returnId)).thenReturn(Optional.empty());
        when(gstSequenceService.nextNumber(GstSequenceType.CREDIT_NOTE)).thenReturn("CN-2627-000001");

        webhookService.handleWebhook(returnId, gatewayRefundId, status, signature);

        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().status()).isEqualTo(RefundStatus.SUCCESS);

        ArgumentCaptor<Return> returnCaptor = ArgumentCaptor.forClass(Return.class);
        verify(returnRepository).save(returnCaptor.capture());
        assertThat(returnCaptor.getValue().status()).isEqualTo(ReturnStatus.REFUND_COMPLETED);

        ArgumentCaptor<GstNote> noteCaptor = ArgumentCaptor.forClass(GstNote.class);
        verify(gstNoteRepository).save(noteCaptor.capture());
        assertThat(noteCaptor.getValue().noteType()).isEqualTo(GstNoteType.CREDIT);
        assertThat(noteCaptor.getValue().number()).isEqualTo("CN-2627-000001");
        assertThat(noteCaptor.getValue().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void handleWebhook_failedStatus_updatesRefundToFailed() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_fail";
        String status = "FAILED";
        String signature = sign(returnId + ":" + gatewayRefundId + ":" + status);

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
        Refund refund = createRefund(returnId, gatewayRefundId, RefundStatus.PENDING);

        when(refundRepository.findByGatewayRefundId(gatewayRefundId)).thenReturn(Optional.of(refund));

        webhookService.handleWebhook(returnId, gatewayRefundId, status, signature);

        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().status()).isEqualTo(RefundStatus.FAILED);

        verify(returnRepository, never()).save(any());
    }

    @Test
    void handleWebhook_alreadyInTerminalState_ignoresAndDoesNotReprocess() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_done";
        String status = "SUCCESS";
        String signature = sign(returnId + ":" + gatewayRefundId + ":" + status);

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
        Refund refund = createRefund(returnId, gatewayRefundId, RefundStatus.SUCCESS);

        when(refundRepository.findByGatewayRefundId(gatewayRefundId)).thenReturn(Optional.of(refund));

        webhookService.handleWebhook(returnId, gatewayRefundId, status, signature);

        verify(refundRepository, never()).save(any());
        verify(returnRepository, never()).save(any());
    }

    @Test
    void handleWebhook_invalidSignature_throwsUnauthorizedException() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_123";
        String status = "SUCCESS";

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);

        assertThatThrownBy(() -> webhookService.handleWebhook(returnId, gatewayRefundId, status, "invalid_sig"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("signature verification failed");

        verify(refundRepository, never()).findByGatewayRefundId(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void handleWebhook_missingSecretOrSignature_throwsUnauthorizedException() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_123";

        assertThatThrownBy(() -> webhookService.handleWebhook(returnId, gatewayRefundId, "SUCCESS", null))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> webhookService.handleWebhook(returnId, gatewayRefundId, "SUCCESS", ""))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void handleWebhook_refundNotFound_logsWarningAndExits() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_unknown";
        String status = "SUCCESS";
        String signature = sign(returnId + ":" + gatewayRefundId + ":" + status);

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
        when(refundRepository.findByGatewayRefundId(gatewayRefundId)).thenReturn(Optional.empty());
        when(refundRepository.findByReturnId(returnId)).thenReturn(Optional.empty());

        webhookService.handleWebhook(returnId, gatewayRefundId, status, signature);

        verify(refundRepository, never()).save(any());
        verify(returnRepository, never()).save(any());
    }

    @Test
    void handleWebhook_dataIntegrityViolation_treatedAsIdempotentSuccess() {
        UUID returnId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_race";
        String status = "SUCCESS";
        String signature = sign(returnId + ":" + gatewayRefundId + ":" + status);

        when(webhookConfig.getWebhookSecret()).thenReturn(SECRET);
        Refund refund = createRefund(returnId, gatewayRefundId, RefundStatus.PENDING);

        when(refundRepository.findByGatewayRefundId(gatewayRefundId)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate key uq_refunds_gateway_refund_id"));

        // Should not throw, treated as idempotent
        webhookService.handleWebhook(returnId, gatewayRefundId, status, signature);
    }
}
