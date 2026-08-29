package com.builddash.backend.infra.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherAndIndexTest {

    private static final byte[] USERS_KEY = new byte[32];
    private static final byte[] ADDRESSES_KEY = new byte[32];
    private static final byte[] PHONE_INDEX_KEY = new byte[32];

    @BeforeAll
    static void initKeys() {
        for (int i = 0; i < 32; i++) {
            USERS_KEY[i] = (byte) i;
            ADDRESSES_KEY[i] = (byte) (i + 100);
            PHONE_INDEX_KEY[i] = (byte) (i + 200);
        }
    }

    @Test
    void encryptDecrypt_roundTrips() {
        String cipherText = PiiCipher.encrypt("+919876543210", USERS_KEY);

        assertThat(cipherText).startsWith("v1:");
        assertThat(cipherText).doesNotContain("+919876543210");
        assertThat(PiiCipher.decrypt(cipherText, USERS_KEY)).isEqualTo("+919876543210");
    }

    @Test
    void encryptionIsRandomized_samePlaintextDifferentCiphertext() {
        String first = PiiCipher.encrypt("same-value", USERS_KEY);
        String second = PiiCipher.encrypt("same-value", USERS_KEY);

        assertThat(first).isNotEqualTo(second);                     // random nonce
        assertThat(PiiCipher.decrypt(first, USERS_KEY)).isEqualTo(PiiCipher.decrypt(second, USERS_KEY));
    }

    @Test
    void keySeparation_usersKeyCannotDecryptAddressCiphertext() {
        String cipherText = PiiCipher.encrypt("secret", ADDRESSES_KEY);

        assertThatThrownBy(() -> PiiCipher.decrypt(cipherText, USERS_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tampered");
    }

    @Test
    void tamperedCiphertext_failsViaGcmAuthentication() {
        String cipherText = PiiCipher.encrypt("secret", USERS_KEY);
        byte[] raw = Base64.getDecoder().decode(cipherText.substring(3));
        raw[raw.length - 1] ^= 0x01;                                // flip one tag bit
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> PiiCipher.decrypt(tampered, USERS_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tampered");
    }

    @Test
    void nullPassthrough_onBothDirections() {
        assertThat(PiiCipher.encrypt(null, USERS_KEY)).isNull();
        assertThat(PiiCipher.decrypt(null, USERS_KEY)).isNull();
    }

    @Test
    void legacyPlaintext_passesThroughDecryptUnchanged() {
        assertThat(PiiCipher.decrypt("+919876543210", USERS_KEY)).isEqualTo("+919876543210");
        assertThat(PiiCipher.isEncrypted("+919876543210")).isFalse();
        assertThat(PiiCipher.isEncrypted("v1:AAAA")).isTrue();
    }

    @Test
    void blindIndex_deterministicPerKey_separatedAcrossKeys() {
        String first = HmacIndex.index("+919876543210", PHONE_INDEX_KEY);
        String second = HmacIndex.index("+919876543210", PHONE_INDEX_KEY);
        String otherKey = HmacIndex.index("+919876543210", USERS_KEY);

        assertThat(first).isEqualTo(second);        // deterministic — lookups work
        assertThat(first).isNotEqualTo(otherKey);   // field keys never cross
        assertThat(HmacIndex.index(null, PHONE_INDEX_KEY)).isNull();
    }
}
