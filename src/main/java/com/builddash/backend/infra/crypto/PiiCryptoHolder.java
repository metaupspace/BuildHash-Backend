package com.builddash.backend.infra.crypto;

/**
 * Bridge between the Spring-managed key provider and JPA AttributeConverters, which
 * Hibernate instantiates outside the Spring context. The ConfigPiiKeyProvider bean
 * publishes the derived-key function here at boot; converters read it. If no provider has
 * initialized it, every conversion FAILS CLOSED — no silent plaintext writes.
 */
public final class PiiCryptoHolder {

    private static volatile PiiKeyProvider provider;

    private PiiCryptoHolder() {
    }

    static void publish(PiiKeyProvider published) {
        provider = published;
    }

    public static PiiKeyProvider provider() {
        PiiKeyProvider current = provider;
        if (current == null) {
            throw new IllegalStateException(
                    "PII_KEY_NOT_CONFIGURED: PII master key was never published — the application context must provide a PiiKeyProvider bean (fail closed, no plaintext writes)");
        }
        return current;
    }
}
