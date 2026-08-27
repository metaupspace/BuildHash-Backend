package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.application.service.RefundWebhookService;
import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundWebhookServiceImpl implements RefundWebhookService {

    private final ReturnRepository returnRepository;
    private final RefundRepository refundRepository;
    private final GstNoteRepository gstNoteRepository;
    private final GstSequenceService gstSequenceService;
    private final PaymentWebhookConfig webhookConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handleWebhook(UUID returnId, String gatewayRefundId, String status, String signature) {
        verifySignature(returnId, gatewayRefundId, status, signature);

        Optional<Refund> refundOpt = refundRepository.findByGatewayRefundId(gatewayRefundId);
        if (refundOpt.isEmpty() && returnId != null) {
            refundOpt = refundRepository.findByReturnId(returnId);
        }

        if (refundOpt.isEmpty()) {
            log.warn("Refund not found for gatewayRefundId {} or returnId {}", gatewayRefundId, returnId);
            return;
        }

        Refund refund = refundOpt.get();
        if (refund.status() == RefundStatus.SUCCESS || refund.status() == RefundStatus.FAILED) {
            log.info("Refund {} is already in terminal status {}, ignoring webhook", refund.id(), refund.status());
            return;
        }

        try {
            if ("SUCCESS".equalsIgnoreCase(status)) {
                Refund successRefund = refund.markSuccess(gatewayRefundId);
                refundRepository.save(successRefund);

                UUID actualReturnId = returnId != null ? returnId : refund.returnId();
                if (actualReturnId != null) {
                    returnRepository.findById(actualReturnId).ifPresent(ret -> {
                        if (ret.status() == ReturnStatus.REFUND_INITIATED) {
                            Return completed = ret.completeRefund();
                            returnRepository.save(completed);
                            eventPublisher.publishEvent(new ReturnStatusChangedEvent(actualReturnId, ReturnStatus.REFUND_INITIATED, ReturnStatus.REFUND_COMPLETED));
                            log.info("Return {} transitioned to REFUND_COMPLETED", actualReturnId);

                            if (gstNoteRepository.findByReturnId(actualReturnId).isEmpty()) {
                                String noteNumber = gstSequenceService.nextNumber(GstSequenceType.CREDIT_NOTE);
                                GstNote creditNote = new GstNote(
                                        UUID.randomUUID(),
                                        actualReturnId,
                                        GstNoteType.CREDIT,
                                        noteNumber,
                                        refund.amount(),
                                        Instant.now(),
                                        Instant.now(),
                                        Instant.now()
                                );
                                gstNoteRepository.save(creditNote);
                                log.info("Generated GstNote CREDIT {} for return {} with amount {}",
                                        noteNumber, actualReturnId, refund.amount());
                            }
                        }
                    });
                }
                log.info("Refund {} marked SUCCESS", refund.id());
                eventPublisher.publishEvent(new RefundCompletedEvent(successRefund.returnId(), successRefund.id()));
            } else if ("FAILED".equalsIgnoreCase(status)) {
                Refund failedRefund = refund.markFailed(gatewayRefundId);
                refundRepository.save(failedRefund);
                log.info("Refund {} marked FAILED", refund.id());
            } else {
                log.warn("Unknown refund status {} for gatewayRefundId {}", status, gatewayRefundId);
            }
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent update for gatewayRefundId {}, treating as idempotent success", gatewayRefundId);
        }
    }

    /**
     * Fail-closed HMAC-SHA256 verification over "returnId:gatewayRefundId:status" (hex-encoded).
     * Missing/blank secret or signature mismatch rejects the webhook outright.
     */
    private void verifySignature(UUID returnId, String gatewayRefundId, String status, String signature) {
        if (signature == null || signature.isBlank()
                || webhookConfig.getWebhookSecret() == null || webhookConfig.getWebhookSecret().isBlank()) {
            throw new UnauthorizedException("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
        }
        String payload = returnId + ":" + gatewayRefundId + ":" + status;
        byte[] expected = hmac(payload, webhookConfig.getWebhookSecret());
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new UnauthorizedException("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
        }
    }

    private static byte[] hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
