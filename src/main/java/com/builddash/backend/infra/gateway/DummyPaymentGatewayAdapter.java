package com.builddash.backend.infra.gateway;

import com.builddash.backend.application.event.PaymentWebhookEvent;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
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
}
