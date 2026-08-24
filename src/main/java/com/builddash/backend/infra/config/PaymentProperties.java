package com.builddash.backend.infra.config;

import com.builddash.backend.domain.port.PaymentWebhookConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment")
@Getter
@Setter
public class PaymentProperties implements PaymentWebhookConfig {

    /** Shared secret for payment-webhook HMAC verification. No default: boot fails without it. */
    private String webhookSecret;
}
