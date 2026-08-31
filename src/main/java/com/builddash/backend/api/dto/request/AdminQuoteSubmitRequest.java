package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-ADMIN controlled quote submission on behalf of a routed vendor. */
public record AdminQuoteSubmitRequest(
        @NotNull UUID vendorId,
        @NotNull @Positive BigDecimal totalAmount,
        @NotNull Instant validUntil
) {
}
