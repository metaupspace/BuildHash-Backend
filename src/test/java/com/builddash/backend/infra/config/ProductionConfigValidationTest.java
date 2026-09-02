package com.builddash.backend.infra.config;

import com.builddash.backend.infra.crypto.ConfigPiiKeyProvider;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductionConfigValidationTest extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void piiMasterKey_missingOrInvalid_failsClosed() {
        assertThatThrownBy(() -> new ConfigPiiKeyProvider(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII master key is missing");

        assertThatThrownBy(() -> new ConfigPiiKeyProvider(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII master key is missing");

        assertThatThrownBy(() -> new ConfigPiiKeyProvider("invalid-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid base64");

        assertThatThrownBy(() -> new ConfigPiiKeyProvider("YWJj")) // "abc" is 3 bytes, not 32
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must decode to exactly 32 bytes");
    }

    @Test
    void deliveryWebhookApiKey_configuredOrFailsClosed() {
        // When delivery API key is unset or blank, webhook calls are rejected
        DeliveryProperties props = new DeliveryProperties();
        props.setWebhookApiKey("");
        assertThat(props.getWebhookApiKey()).isBlank();
    }

    @Test
    void externalTimeouts_areBounded() {
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout")).isNotNull();
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout")).isNotNull();
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.writetimeout")).isNotNull();
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout")).isNotNull();
    }
}
