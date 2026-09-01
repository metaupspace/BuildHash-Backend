package com.builddash.backend.infra.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H0.1 regression guard: committed secret fallbacks must never come back. The
 * startup fail-closed itself is @NotBlank/@Validated on the properties classes and
 * @Value-without-default in S3ObjectStorageAdapter — this pins the configuration
 * surface those mechanisms read.
 */
class SecretsFailClosedTest {

    @Test
    void noJwtSecretFallbackInAnyShippedProfile() throws Exception {
        assertThat(read("application-dev.yaml"))
                .doesNotContain("super_secret_key")
                .doesNotContain("${JWT_SECRET:");
        assertThat(read("application.yaml")).doesNotContain("${JWT_SECRET:");
    }

    @Test
    void noWebhookSecretFallbackInAnyShippedProfile() throws Exception {
        assertThat(read("application-dev.yaml"))
                .doesNotContain("dev-only-webhook-secret")
                .doesNotContain("${PAYMENT_WEBHOOK_SECRET:");
        assertThat(read("application.yaml")).doesNotContain("${PAYMENT_WEBHOOK_SECRET:");
    }

    @Test
    void noMinioadminCredentialDefaultsAnywhere() throws Exception {
        for (String yaml : List.of("application.yaml", "application-dev.yaml")) {
            assertThat(read(yaml)).doesNotContain("minioadmin");
        }
    }

    @Test
    void blankJwtSecretFailsValidation() {
        JwtProperties props = new JwtProperties();
        props.setSecret("  ");
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void blankWebhookSecretFailsValidation() {
        PaymentProperties props = new PaymentProperties();
        props.setWebhookSecret("");
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<PaymentProperties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
    }

    private static String read(String classpathFile) throws Exception {
        try (InputStream in = SecretsFailClosedTest.class.getClassLoader().getResourceAsStream(classpathFile)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath file: " + classpathFile);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
