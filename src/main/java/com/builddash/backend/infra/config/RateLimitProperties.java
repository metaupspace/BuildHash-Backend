package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Yaml-driven rate-limit rules (PLAN_PHASE8 decision 7), pattern per OtpProperties/
 * SupportProperties. Rule NAME is the limiter bucket, e.g.:
 *
 * <pre>
 * security:
 *   rate-limit:
 *     rules:
 *       search: { path: /search/**,  limit: 30, window: 1m }
 *       google: { path: /auth/google, limit: 10, window: 1m }
 * </pre>
 *
 * NOTE: one nesting level ("rules:") beyond PLAN_PHASE8 §8's skeleton — Boot 3's Binder binds
 * Map properties through a named field and will not hydrate arbitrary keys into the prefix
 * root, so the skeleton's literal shape cannot bind. Adding a rule stays yaml-only.
 */
@Component
@ConfigurationProperties(prefix = "security.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private Map<String, Rule> rules = new HashMap<>();

    @Getter
    @Setter
    public static class Rule {

        /** Servlet path pattern, e.g. /search/** (PathPattern syntax). */
        private String path;

        /** Optional HTTP method restriction; null/blank = any method. OPTIONS never counts. */
        private String method;

        private int limit;

        private Duration window;
    }
}
