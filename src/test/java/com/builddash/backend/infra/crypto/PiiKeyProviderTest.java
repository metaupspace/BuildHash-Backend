package com.builddash.backend.infra.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiKeyProviderTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static final String FIELD_A = "pii:users:string:aes";
    private static final String FIELD_B = "pii:addresses:string:aes";

    @Test
    void blankKey_failsConstructionFailClosed() {
        assertThatThrownBy(() -> new ConfigPiiKeyProvider(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> new ConfigPiiKeyProvider(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> new ConfigPiiKeyProvider("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void invalidBase64Key_failsConstruction() {
        assertThatThrownBy(() -> new ConfigPiiKeyProvider("not!!valid!!base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void wrongLengthKey_failsConstruction() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new ConfigPiiKeyProvider(sixteenBytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void derivedKeys_areDeterministicAndLabelSeparated() {
        ConfigPiiKeyProvider provider = new ConfigPiiKeyProvider(VALID_KEY);

        byte[] a1 = provider.derivedKey(FIELD_A);
        byte[] a2 = provider.derivedKey(FIELD_A);
        byte[] b = provider.derivedKey(FIELD_B);

        assertThat(a1).isEqualTo(a2);           // deterministic
        assertThat(a1).hasSize(32);              // AES-256-grade subkey
        assertThat(a1).isNotEqualTo(b);          // labels never collide
    }

    @Test
    void differentMasterKeys_neverDeriveTheSameSubkey() {
        byte[] otherMaster = new byte[32];
        for (int i = 0; i < 32; i++) {
            otherMaster[i] = (byte) (i + 7);
        }
        ConfigPiiKeyProvider first = new ConfigPiiKeyProvider(VALID_KEY);
        ConfigPiiKeyProvider second = new ConfigPiiKeyProvider(Base64.getEncoder().encodeToString(otherMaster));

        assertThat(first.derivedKey(FIELD_A)).isNotEqualTo(second.derivedKey(FIELD_A));
    }

    @Test
    void constructionPublishesProviderToHolder() {
        ConfigPiiKeyProvider provider = new ConfigPiiKeyProvider(VALID_KEY);
        assertThat(PiiCryptoHolder.provider()).isSameAs(provider);
    }
}
