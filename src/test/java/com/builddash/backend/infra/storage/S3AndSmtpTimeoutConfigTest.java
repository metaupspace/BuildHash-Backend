package com.builddash.backend.infra.storage;

import com.builddash.backend.infra.config.RateLimitProperties;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class S3AndSmtpTimeoutConfigTest extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Test
    void hikariConnectionPool_configuredWithBoundedConcurrency() {
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("20000");
        assertThat(environment.getProperty("spring.datasource.hikari.idle-timeout"))
                .isEqualTo("300000");
        assertThat(environment.getProperty("spring.datasource.hikari.max-lifetime"))
                .isEqualTo("1800000");
    }

    @Test
    void smtpTimeouts_configuredWithExplicitBounds() {
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout"))
                .isEqualTo("5000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout"))
                .isEqualTo("10000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.writetimeout"))
                .isEqualTo("10000");
    }

    @Test
    void rateLimitRules_coverHighCostMutations() {
        var rules = rateLimitProperties.getRules();
        assertThat(rules).containsKey("search");
        assertThat(rules).containsKey("google");
        assertThat(rules).containsKey("review-create");
        assertThat(rules).containsKey("question-create");
        assertThat(rules).containsKey("answer-create");
        assertThat(rules).containsKey("ticket-create");
        assertThat(rules).containsKey("return-create");

        assertThat(rules.get("review-create").getPath()).isEqualTo("/products/{id}/reviews");
        assertThat(rules.get("question-create").getPath()).isEqualTo("/products/{id}/questions");
        assertThat(rules.get("answer-create").getPath()).isEqualTo("/questions/{id}/answers");
        assertThat(rules.get("ticket-create").getPath()).isEqualTo("/support/tickets");
        assertThat(rules.get("return-create").getPath()).isEqualTo("/orders/{id}/return");

        assertThat(rules.get("review-create").getLimit()).isPositive();
        assertThat(rules.get("question-create").getLimit()).isPositive();
        assertThat(rules.get("answer-create").getLimit()).isPositive();
        assertThat(rules.get("ticket-create").getLimit()).isPositive();
        assertThat(rules.get("return-create").getLimit()).isPositive();
    }
}
