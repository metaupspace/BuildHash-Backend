package com.builddash.backend.infra.persistence.converter;

import com.builddash.backend.infra.crypto.ConfigPiiKeyProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AddressPiiDoubleConverterTest {

    private final AddressPiiDoubleConverter converter = new AddressPiiDoubleConverter();

    @BeforeAll
    static void publishTestKey() {
        new ConfigPiiKeyProvider(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void roundTrip_doubleInCiphertextOut_doubleBack() {
        String stored = converter.convertToDatabaseColumn(19.076090);

        assertThat(stored).startsWith("v1:").doesNotContain("19.076090");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(19.076090);
    }

    @Test
    void nullPassthrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void legacyPlaintextDouble_readsBackUnchanged() {
        // Pre-V22 rows stored raw doubles; V22 casts them to text ("19.07609"), which must
        // pass through decrypt unchanged and parse back to the same Double.
        assertThat(converter.convertToEntityAttribute("19.076090")).isEqualTo(19.076090);
    }
}
