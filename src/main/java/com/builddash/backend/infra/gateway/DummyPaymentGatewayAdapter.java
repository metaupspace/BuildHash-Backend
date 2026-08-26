package com.builddash.backend.infra.gateway;

import com.builddash.backend.application.event.PaymentWebhookEvent;
import com.builddash.backend.application.event.RefundWebhookEvent;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.RefundReference;
import com.builddash.backend.domain.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DummyPaymentGatewayAdapter implements PaymentGateway {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PaymentReference initiate(UUID orderId, BigDecimal amount) {
        if (amount != null && amount.compareTo(new BigDecimal("9999")) == 0) {
            throw new RuntimeException("Simulated gateway connection timeout");
        }

        String txId = "dummy_tx_" + UUID.randomUUID();
        String url = "https://dummy.gateway.local/pay/" + txId;

        // Fire async simulated webhook after a short delay
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            // Default to SUCCESS. Could use amount thresholds to simulate failures.
            eventPublisher.publishEvent(new PaymentWebhookEvent(orderId, "SUCCESS", "dummy_sig"));
        });

        return new PaymentReference(txId, url);
    }

    @Override
    public RefundReference refund(String transactionId, BigDecimal amount) {
        if (amount != null && amount.compareTo(new BigDecimal("9999")) == 0) {
            throw new RuntimeException("Simulated gateway connection timeout");
        }

        String gatewayRefundId = "dummy_ref_" + UUID.randomUUID();
        String status = (amount != null && amount.compareTo(new BigDecimal("9998")) == 0) ? "FAILED" : "SUCCESS";

        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            eventPublisher.publishEvent(new RefundWebhookEvent(null, gatewayRefundId, status, "dummy_sig"));
        });

        return new RefundReference(gatewayRefundId, "PENDING");
    }
}
