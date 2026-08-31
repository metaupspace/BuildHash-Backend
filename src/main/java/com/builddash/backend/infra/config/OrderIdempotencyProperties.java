package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Idempotency rolling window (PLAN_PHASE8 decision 10): a key older than the window reads
 * as "not found" (filter-on-read) and is physically removed nightly (purge sweep).
 * Feature-doc §11 asked for "rolling window (e.g. 24h)" — 24 is the shipped default.
 */
@Component
@ConfigurationProperties(prefix = "orders")
@Getter
@Setter
public class OrderIdempotencyProperties {

    private int idempotencyWindowHours = 24;
}
