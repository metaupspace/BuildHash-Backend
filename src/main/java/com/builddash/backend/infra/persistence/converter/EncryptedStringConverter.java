package com.builddash.backend.infra.persistence.converter;

import com.builddash.backend.infra.crypto.PiiCipher;
import com.builddash.backend.infra.crypto.PiiCryptoHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparent AES-GCM at the JPA boundary (PLAN_PHASE8 decision 1): domain code and every
 * existing test see plaintext Strings; the column holds v1:-prefixed ciphertext. Reads
 * pass legacy (not-yet-backfilled) plaintext through unchanged; writes ALWAYS encrypt —
 * after this converter ships, no new plaintext can enter the column. Key separation is
 * per entity (subclass overrides keyLabel) — a users-key ciphertext cannot decrypt under
 * the addresses key and vice versa.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    /** Entity-qualified key label — subclasses bind their entity's subkey. */
    protected String keyLabel() {
        return "pii:generic:string:aes";
    }

    @Override
    public final String convertToDatabaseColumn(String attribute) {
        return PiiCipher.encrypt(attribute, PiiCryptoHolder.provider().derivedKey(keyLabel()));
    }

    @Override
    public final String convertToEntityAttribute(String dbData) {
        return PiiCipher.decrypt(dbData, PiiCryptoHolder.provider().derivedKey(keyLabel()));
    }
}
