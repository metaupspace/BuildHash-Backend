package com.builddash.backend.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPageCursorTest {

    @Test
    void encode_thenDecode_roundTrips() {
        ProductPageCursor original = new ProductPageCursor(Instant.parse("2026-08-17T10:15:30.123Z"), UUID.randomUUID());

        ProductPageCursor decoded = ProductPageCursor.decode(original.encode());

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void decode_notBase64_throwsIllegalArgument() {
        assertThatThrownBy(() -> ProductPageCursor.decode("!!!not valid base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_validBase64WithoutSeparator_throwsIllegalArgument() {
        String noSeparator = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("no-separator-here".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ProductPageCursor.decode(noSeparator))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
