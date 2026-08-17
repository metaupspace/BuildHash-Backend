package com.builddash.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin-managed platform master data (HSN code -> GST %), used by the pricing engine
 * once Catalog phase tags products with an HSN code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HsnGstRate {

    private String hsnCode;
    private String description;
    private BigDecimal gstRatePercent;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;
}
