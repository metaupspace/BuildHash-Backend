package com.builddash.backend.infra.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM with a "v1:" version prefix (the reserved rotation marker, OQ-2): the stored
 * form is v1:base64(nonce[12] || ciphertext+tag[16]). GCM's auth tag makes tampering a
 * hard failure, and the prefix lets the backfill sweeper distinguish encrypted from legacy
 * plaintext rows and lets a future key version coexist during rotation.
 */
public final class PiiCipher {

    public static final String VERSION_PREFIX = "v1:";

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PiiCipher() {
    }

    public static boolean isEncrypted(String storedValue) {
        return storedValue != null && storedValue.startsWith(VERSION_PREFIX);
    }

    /** Returns null for null; encrypts everything else (never stores plaintext). */
    public static String encrypt(String plaintext, byte[] fieldKey) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fieldKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + cipherText.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(cipherText, 0, combined, nonce.length, cipherText.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("PII encryption failed", e);
        }
    }

    /**
     * Decrypts v1-prefixed values; any other non-null value passes through unchanged —
     * the transition bridge that keeps not-yet-backfilled plaintext rows readable (and
     * only readable: writes always encrypt). Tampered ciphertext fails via GCM auth.
     */
    public static String decrypt(String storedValue, byte[] fieldKey) {
        if (storedValue == null) {
            return null;
        }
        if (!isEncrypted(storedValue)) {
            return storedValue;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue.substring(VERSION_PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(fieldKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, combined, 0, NONCE_LENGTH));
            byte[] plain = cipher.doFinal(combined, NONCE_LENGTH, combined.length - NONCE_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII decryption failed (wrong key or tampered ciphertext)", e);
        }
    }
}
