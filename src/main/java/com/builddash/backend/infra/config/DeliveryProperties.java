package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "delivery")
@Getter
@Setter
public class DeliveryProperties {

    /**
     * Secret API key for delivery-partner webhooks (X-API-Key).
     * Must be explicitly configured in the environment; fail-closed if empty.
     */
    private String webhookApiKey;

    /**
     * Modification window duration in minutes for reschedule/cancel on CONFIRMED orders.
     */
    private int modificationWindowMinutes = 15;
}
