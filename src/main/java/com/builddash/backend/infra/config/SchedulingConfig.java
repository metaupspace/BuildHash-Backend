package com.builddash.backend.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Gated out of the test profile — same intent as disabling the RabbitMQ listener autostart in
 * application-test.yaml: background pollers (CatalogOutboxRelay) shouldn't fire against a
 * broker that isn't there during every IT's context lifetime.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
