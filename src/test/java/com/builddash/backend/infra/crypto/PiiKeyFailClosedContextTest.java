package com.builddash.backend.infra.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fail-closed startup proof (PLAN_PHASE8 decision 2): a Spring context WITHOUT
 * security.pii.master-key must refuse to start — the missing env var is a loud boot
 * failure, never a silently-unencrypted deployment. AbstractIntegrationTest sets the
 * property statically for shared contexts, so the missing-key case clears it first.
 */
class PiiKeyFailClosedContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigPiiKeyProvider.class);

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Test
    void contextWithoutMasterKey_failsToStart() {
        String original = System.getProperty("security.pii.master-key");
        System.clearProperty("security.pii.master-key");
        try {
            runner.withPropertyValues("security.pii.master-key=").run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootCause(context.getStartupFailure()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PII master key is missing");
            });
        } finally {
            if (original != null) {
                System.setProperty("security.pii.master-key", original);
            }
        }
    }

    @Test
    void contextWithInvalidKey_failsToStart() {
        runner.withPropertyValues("security.pii.master-key=not!!valid!!base64").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).hasMessageContaining("base64");
        });
    }

    @Test
    void contextWithWrongLengthKey_failsToStart() {
        String sixteenBytes = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        runner.withPropertyValues("security.pii.master-key=" + sixteenBytes).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).hasMessageContaining("32 bytes");
        });
    }

    @Test
    void contextWithValidKey_boots() {
        String valid = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        runner.withPropertyValues("security.pii.master-key=" + valid)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
