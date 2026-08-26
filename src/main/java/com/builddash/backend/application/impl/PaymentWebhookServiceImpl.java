package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.OrderConfirmedEvent;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Order;
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

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.status() != OrderStatus.PAYMENT_PENDING) {
            log.info("Order {} is already {}, ignoring webhook", orderId, order.status());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            Order confirmed = order.confirm();
            orderRepository.save(confirmed);
            eventPublisher.publishEvent(new OrderConfirmedEvent(orderId));
            updatePaymentStatus(orderId, PaymentStatus.SUCCESS);
            // Consume, not release: the confirmed order still occupies delivery capacity
            deliverySlotService.consumeLock(confirmed.deliverySlotLockId(), confirmed.userId());
            log.info("Order {} confirmed successfully", orderId);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            updatePaymentStatus(orderId, PaymentStatus.FAILED);
            log.info("Payment failed for order {}", orderId);
        } else {
            log.warn("Unknown payment status {} for order {}", status, orderId);
        }
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
