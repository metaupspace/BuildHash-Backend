package com.builddash.backend.infra.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * FAIL CLOSED, NO DEFAULT (PLAN_PHASE8 decision 2, delivery X-API-Key precedent): the
 * constructor rejects a missing/blank/misdecoded/wrong-length master key with
 * IllegalStateException, so the Spring context refuses to boot — a forgotten env var is a
 * loud startup failure, never a silently-unencrypted deployment. There is deliberately NO
 * default value in application.yaml; test contexts set their own key.
 */
@Component
public class ConfigPiiKeyProvider implements PiiKeyProvider {

    static final String MASTER_KEY_PROPERTY = "security.pii.master-key";

    private final byte[] masterKey;

    public ConfigPiiKeyProvider(@Value("${security.pii.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("PII master key is missing: set PII_MASTER_KEY (base64, 32 bytes) — no default exists by design");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(masterKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PII master key is not valid base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("PII master key must decode to exactly 32 bytes (AES-256), got " + decoded.length);
        }
        this.masterKey = decoded;
        // Publish for the Hibernate-instantiated converters (outside the Spring context).
        PiiCryptoHolder.publish(this);
    }

    @Override
    public byte[] derivedKey(String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("PII key derivation failed", e);
        }
    }
}
