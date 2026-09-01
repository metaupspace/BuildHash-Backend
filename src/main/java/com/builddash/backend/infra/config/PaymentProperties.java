package com.builddash.backend.infra.config;

import com.builddash.backend.domain.port.PaymentWebhookConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "payment")
@Getter
@Setter
public class PaymentProperties implements PaymentWebhookConfig {

    /** Shared secret for payment-webhook HMAC verification. @NotBlank makes the
     * javadoc claim true: missing or blank fails startup, not first webhook use. */
    @NotBlank
    private String webhookSecret;
}
