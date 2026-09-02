package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.OrderConfirmedEvent;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliverySlotService deliverySlotService;
    private final PaymentWebhookConfig webhookConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void handleWebhook(UUID orderId, String status, String signature) {
        verifySignature(orderId, status, signature);

        // Same lock choke point as StaleOrderSweepServiceImpl.sweepOrder (H1.5): whichever
        // of the two commits first wins deterministically, the loser re-reads the
        // post-commit status once it unblocks.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.status() != OrderStatus.PAYMENT_PENDING) {
            if ("SUCCESS".equalsIgnoreCase(status) && order.status() == OrderStatus.CANCELLED) {
                // H1.5: the stale-order sweep won the race and cancelled the order before
                // this SUCCESS delivery arrived. The gateway has already captured money —
                // dropping the webhook here would silently lose it. Do not resurrect the
                // order (its delivery-slot capacity is already released); record the
                // captured payment as durable evidence and raise a CRITICAL signal for
                // manual compensating-refund reconciliation. No new Return-scoped refund
                // is fabricated.
                recordCapturedPaymentOnCancelledOrder(orderId);
            } else {
                log.info("Order {} is already {}, ignoring webhook", orderId, order.status());
            }
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            // H1.2: CONFIRMED must correspond to a durable payment record. A SUCCESS
            // webhook with no matching Payment row at all is an integrity anomaly, not a
            // trustworthy confirmation signal — never confirm on its word alone.
            Optional<Payment> payment = paymentRepository.findLatestByOrderId(orderId);
            if (payment.isEmpty()) {
                log.error("SUCCESS webhook for order {} but no Payment row exists; refusing to confirm, leaving PAYMENT_PENDING for reconciliation", orderId);
                return;
            }

            Order confirmed = order.confirm();
            orderRepository.save(confirmed);
            eventPublisher.publishEvent(new OrderConfirmedEvent(orderId));
            paymentRepository.save(payment.get().markSuccess(payment.get().transactionId()));
            // Consume, not release: the confirmed order still occupies delivery capacity.
            // H2.7: a false return means the lock was no longer ACTIVE (expired and swept,
            // or released by a racing path) — the order IS paid and confirmed, so rolling
            // back is wrong; surface a CRITICAL capacity-reconciliation signal instead,
            // same discipline as the H1 captured-payment-on-cancelled-order path.
            boolean consumed = deliverySlotService.consumeLock(confirmed.deliverySlotLockId(), confirmed.userId());
            if (!consumed) {
                log.error("CRITICAL: order {} confirmed but its delivery-slot lock {} was no longer ACTIVE — "
                        + "delivery capacity may have been returned while the order is still scheduled, "
                        + "manual capacity reconciliation required", orderId, confirmed.deliverySlotLockId());
            }
            log.info("Order {} confirmed successfully", orderId);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            updatePaymentStatus(orderId, PaymentStatus.FAILED);
            log.info("Payment failed for order {}", orderId);
        } else {
            log.warn("Unknown payment status {} for order {}", status, orderId);
        }
    }

    private void recordCapturedPaymentOnCancelledOrder(UUID orderId) {
        Optional<Payment> payment = paymentRepository.findLatestByOrderId(orderId);
        if (payment.isEmpty()) {
            log.error("CRITICAL: SUCCESS webhook for cancelled order {} with no Payment row at all — captured money cannot be correlated to any durable record", orderId);
            return;
        }
        paymentRepository.save(payment.get().markSuccess(payment.get().transactionId()));
        log.error("CRITICAL: payment captured for order {} after it was already CANCELLED (stale-order sweep race) — manual compensating-refund reconciliation required", orderId);
    }

    /**
     * Fail-closed HMAC-SHA256 verification over "orderId:status" (hex-encoded).
     * Missing/blank secret or signature mismatch rejects the webhook outright.
     */
    private void verifySignature(UUID orderId, String status, String signature) {
        if (signature == null || signature.isBlank()
                || webhookConfig.getWebhookSecret() == null || webhookConfig.getWebhookSecret().isBlank()) {
            throw new UnauthorizedException("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
        }
        byte[] expected = hmac(orderId + ":" + status, webhookConfig.getWebhookSecret());
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

    private void updatePaymentStatus(UUID orderId, PaymentStatus status) {
        // ponytail: matches latest Payment row since dummy gateway doesn't provide transactionId. Real gateway should match on transactionId.
        paymentRepository.findLatestByOrderId(orderId).ifPresent(payment -> {
            com.builddash.backend.domain.model.Payment updated = status == PaymentStatus.SUCCESS ?
                payment.markSuccess(payment.transactionId()) : payment.markFailed(payment.transactionId());
            paymentRepository.save(updated);
        });
    }
}
