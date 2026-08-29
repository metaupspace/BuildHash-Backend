package com.builddash.backend.infra.persistence.converter;

import com.builddash.backend.infra.crypto.ConfigPiiKeyProvider;
import com.builddash.backend.infra.crypto.PiiCryptoHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private final UserPiiStringConverter converter = new UserPiiStringConverter();
    private final AddressPiiStringConverter otherEntityConverter = new AddressPiiStringConverter();

    @BeforeAll
    static void publishTestKey() {
        // Converters are Hibernate-instantiated; the holder needs a provider, exactly as
        // the Spring context would supply at boot.
        new ConfigPiiKeyProvider(Base64.getEncoder().encodeToString(new byte[32]));
        PiiCryptoHolder.provider();
    }

    @Test
    void roundTrip_plaintextInCiphertextOut_plaintextBack() {
        String stored = converter.convertToDatabaseColumn("+919876543210");

        assertThat(stored).startsWith("v1:").doesNotContain("+919876543210");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("+919876543210");
    }

    @Test
    void nullPassthrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void legacyPlaintextRow_readsBackUnchanged() {
        // The transition bridge: a not-yet-backfilled plaintext value must keep working.
        assertThat(converter.convertToEntityAttribute("+919876543210")).isEqualTo("+919876543210");
    }

    @Test
    void entityKeySeparation_userCiphertextNotReadableByAddressConverter() {
        String userStored = converter.convertToDatabaseColumn("secret");
        String addressStored = otherEntityConverter.convertToDatabaseColumn("secret");

        assertThat(converter.convertToEntityAttribute(userStored)).isEqualTo("secret");
        assertThat(addressStored).isNotEqualTo(userStored);
    }
}
