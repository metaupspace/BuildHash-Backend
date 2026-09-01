package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * cartId/siteId are the 9-D B2B checkout inputs: null cartId = primary (B2C) cart;
 * a B2B_DRAFT cart id selects the B2B branch. siteId is optional, validated against
 * the cart's company sites and stamped onto the order (site-scoped approvals).
 * Both ignored for B2C.
 */
public record CreateOrderRequest(
        @NotNull UUID addressId,
        @NotNull UUID slotId,
        @NotNull LocalDate slotDate,
        BigDecimal expectedTotal,
        UUID cartId,
        UUID siteId
) {
}
