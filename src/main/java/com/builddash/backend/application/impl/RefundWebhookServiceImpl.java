package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.application.service.RefundWebhookService;
import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.application.service.ApplicationMetrics;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationMetrics metrics;
    private final RefundRepository refundRepository;
    private final GstNoteRepository gstNoteRepository;
    private final GstSequenceService gstSequenceService;
    private final PaymentWebhookConfig webhookConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handleWebhook(UUID returnId, String gatewayRefundId, String status, String signature) {
        verifySignature(returnId, gatewayRefundId, status, signature);

        Optional<Refund> refundLookup = refundRepository.findByGatewayRefundId(gatewayRefundId);
        if (refundLookup.isEmpty() && returnId != null) {
            refundLookup = refundRepository.findByReturnId(returnId);
        }

        if (refundLookup.isEmpty()) {
            log.warn("Refund not found for gatewayRefundId {} or returnId {}", gatewayRefundId, returnId);
            return;
        }

        UUID actualReturnId = refundLookup.get().returnId();
        UUID refundId = refundLookup.get().id();

        // Canonical lock order (RETURN -> REFUND), identical to RefundServiceImpl.finalizeClaim:
        // a concurrent duplicate webhook delivery or a concurrent finalize serializes
        // against this call instead of racing it (H1.1/H1.4b).
        Return lockedReturn = returnRepository.findByIdForUpdate(actualReturnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + actualReturnId));
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", "Refund not found: " + refundId));

        // Re-check after acquiring the lock: a concurrent delivery that got here first
        // has already committed and released the lock by the time we unblock. This makes
        // every delivery after the first an idempotent no-op — no unique constraint needed
        // to detect it.
        if (refund.status() == RefundStatus.SUCCESS || refund.status() == RefundStatus.FAILED) {
            log.info("Refund {} is already in terminal status {}, ignoring webhook", refund.id(), refund.status());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            Refund successRefund = refund.markSuccess(gatewayRefundId);
            refundRepository.save(successRefund);

            if (lockedReturn.status() == ReturnStatus.REFUND_INITIATED) {
                Return completed = lockedReturn.completeRefund();
                returnRepository.save(completed);
                eventPublisher.publishEvent(new ReturnStatusChangedEvent(actualReturnId, ReturnStatus.REFUND_INITIATED, ReturnStatus.REFUND_COMPLETED));
                log.info("Return {} transitioned to REFUND_COMPLETED", actualReturnId);

                // Only the delivery that wins the Refund-row lock and finds the Return
                // still REFUND_INITIATED reaches here — a duplicate delivery already
                // returned above via the terminal re-check, so this insert happens
                // at most once per return. uq_gst_notes_return_type (V31) is a pure
                // backstop, not the concurrency mechanism.
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
            log.info("Refund {} marked SUCCESS", refund.id());
            metrics.recordRefundOutcome("SUCCESS");
            eventPublisher.publishEvent(new RefundCompletedEvent(successRefund.returnId(), successRefund.id()));
        } else if ("FAILED".equalsIgnoreCase(status)) {
            Refund failedRefund = refund.markFailed(gatewayRefundId);
            refundRepository.save(failedRefund);
            log.info("Refund {} marked FAILED", refund.id());
            metrics.recordRefundOutcome("FAILED");
        } else {
            log.warn("Unknown refund status {} for gatewayRefundId {}", status, gatewayRefundId);
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
