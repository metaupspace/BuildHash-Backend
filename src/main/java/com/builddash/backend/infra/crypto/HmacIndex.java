package com.builddash.backend.infra.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Blind index (PLAN_PHASE8 decision 3): HMAC-SHA256 over the value with the field's index
 * subkey, base64-stored in the *_idx column. Deterministic by design so equality lookups
 * and unique constraints survive encryption; keyed per-field so an idx leak never crosses
 * into another field's domain.
 */
public final class HmacIndex {

    private HmacIndex() {
    }

    public static String index(String value, byte[] indexKey) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(indexKey, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("PII blind-index computation failed", e);
        }
    }
}
