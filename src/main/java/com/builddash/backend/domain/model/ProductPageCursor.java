package com.builddash.backend.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination cursor for the product list keyset query: (createdAt, id) is the tiebreak
 * pair that makes ordering strict even when two products share a createdAt timestamp — unlike
 * a random UUID alone, which carries no insertion-order guarantee on Postgres.
 */
public record ProductPageCursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ProductPageCursor decode(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        int separatorIndex = raw.indexOf('|');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Malformed cursor");
        }
        return new ProductPageCursor(Instant.parse(raw.substring(0, separatorIndex)), UUID.fromString(raw.substring(separatorIndex + 1)));
    }
}
