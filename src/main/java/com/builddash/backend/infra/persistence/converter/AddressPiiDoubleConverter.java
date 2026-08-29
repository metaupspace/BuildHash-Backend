package com.builddash.backend.infra.persistence.converter;

import com.builddash.backend.infra.crypto.PiiCipher;
import com.builddash.backend.infra.crypto.PiiCryptoHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AES-GCM for Double PII (addresses lat/lng): stored as v1:-prefixed ciphertext STRING,
 * domain stays Double. Same write-always-encrypt / legacy-passthrough-read contract as
 * EncryptedStringConverter.
 */
@Converter
public class AddressPiiDoubleConverter implements AttributeConverter<Double, String> {

    @Override
    public String convertToDatabaseColumn(Double attribute) {
        if (attribute == null) {
            return null;
        }
        return PiiCipher.encrypt(Double.toString(attribute), PiiCryptoHolder.provider().derivedKey("pii:addresses:double:aes"));
    }

    @Override
    public Double convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String plain = PiiCipher.decrypt(dbData, PiiCryptoHolder.provider().derivedKey("pii:addresses:double:aes"));
        // Legacy plaintext doubles pass through decrypt unchanged; only v1: values were encrypted.
        return Double.valueOf(plain);
    }
}
