package com.builddash.backend.infra.config;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-category SLA hours (PLAN_PHASE7 5(e)): policy that can change without a recompile,
 * modification-window-minutes convention. Yaml overrides any subset — code defaults below
 * cover the rest, so a missing key never blows up ticket creation.
 */
@Component
@ConfigurationProperties(prefix = "support")
@Getter
@Setter
public class SupportProperties {

    /** Keyed by category name in yaml: support.sla.ORDER_ISSUE: 24h */
    private Map<String, Duration> sla = new HashMap<>();

    public Duration slaFor(SupportTicketCategory category) {
        Duration configured = sla.get(category.name());
        if (configured != null && !configured.isNegative() && !configured.isZero()) {
            return configured;
        }
        return DEFAULT_SLA.get(category);
    }

    private static final Map<SupportTicketCategory, Duration> DEFAULT_SLA = Map.of(
            SupportTicketCategory.ORDER_ISSUE, Duration.ofHours(24),
            SupportTicketCategory.PAYMENT_ISSUE, Duration.ofHours(4),
            SupportTicketCategory.RETURN_REFUND_ISSUE, Duration.ofHours(24),
            SupportTicketCategory.DELIVERY_ISSUE, Duration.ofHours(4),
            SupportTicketCategory.PRODUCT_QUERY, Duration.ofHours(48),
            SupportTicketCategory.OTHER, Duration.ofHours(48));
}
