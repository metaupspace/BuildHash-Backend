package com.builddash.backend.infra.gateway;

import com.builddash.backend.application.event.PaymentWebhookEvent;
import com.builddash.backend.application.event.RefundWebhookEvent;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.exception.AmbiguousGatewayException;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.RefundReference;
import com.builddash.backend.domain.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DummyPaymentGatewayAdapter implements PaymentGateway {

    private final ApplicationEventPublisher eventPublisher;

    private final java.util.Map<UUID, PaymentStatus> simulatedOrderStatuses = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public PaymentReference initiate(UUID orderId, BigDecimal amount) {
        if (amount != null && amount.compareTo(new BigDecimal("9999")) == 0) {
            throw new RuntimeException("Simulated gateway connection timeout");
        }

        String txId = "dummy_tx_" + UUID.randomUUID();
        String url = "https://dummy.gateway.local/pay/" + txId;
        simulatedOrderStatuses.put(orderId, PaymentStatus.SUCCESS);

        // Fire async simulated webhook after a short delay
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            // Default to SUCCESS. Could use amount thresholds to simulate failures.
            eventPublisher.publishEvent(new PaymentWebhookEvent(orderId, "SUCCESS", "dummy_sig"));
        });

        return new PaymentReference(txId, url);
    }

    public void setSimulatedOrderStatus(UUID orderId, PaymentStatus status) {
        if (status == null) {
            simulatedOrderStatuses.remove(orderId);
        } else {
            simulatedOrderStatuses.put(orderId, status);
        }
    }

    @Override
    public RefundReference refund(String transactionId, BigDecimal amount, UUID returnId) {
        if (amount != null && amount.compareTo(new BigDecimal("9999")) == 0) {
            // Transport failure, not a rejection: the gateway may or may not have
            // processed this refund. Never surface this as a definitive FAILED (H1.6).
            throw new AmbiguousGatewayException("Simulated gateway connection timeout");
        }

        String gatewayRefundId = "dummy_ref_" + UUID.randomUUID();
        String status = (amount != null && amount.compareTo(new BigDecimal("9998")) == 0) ? "FAILED" : "SUCCESS";

        // Echoes returnId (H1.4): lets the webhook recover the claim via findByReturnId
        // even if the local finalize transaction never commits the gatewayRefundId.
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            eventPublisher.publishEvent(new RefundWebhookEvent(returnId, gatewayRefundId, status, "dummy_sig"));
        });

        return new RefundReference(gatewayRefundId, "PENDING");
    }

    @Override
    public Optional<PaymentStatus> queryStatus(String transactionId, UUID orderId) {
        if (transactionId != null) {
            if (transactionId.contains("fail")) {
                return Optional.of(PaymentStatus.FAILED);
            }
            if (transactionId.contains("timeout")) {
                return Optional.empty();
            }
            if (transactionId.contains("pending")) {
                return Optional.of(PaymentStatus.PENDING);
            }
            return Optional.of(PaymentStatus.SUCCESS);
        }
        if (orderId != null && simulatedOrderStatuses.containsKey(orderId)) {
            return Optional.ofNullable(simulatedOrderStatuses.get(orderId));
        }
        return Optional.empty();
    }
}
