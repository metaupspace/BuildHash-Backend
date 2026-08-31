package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PoRowStatus;

import java.util.UUID;

/**
 * Exactly one row record per nonblank source row, including INVALID ones.
 * productSlug/quantity are nullable so malformed cell values stay representable
 * verbatim — never truncated, never dropped. The XLSX header column is "sku";
 * it maps onto the catalog's business identifier products.slug (no SKU field
 * exists — see PoWorkbookParser).
 */
public record PoImportRow(
        UUID id,
        UUID importId,
        int rowIndex,
        String productSlug,
        Integer quantity,
        PoRowStatus status,
        String errorCode
) {
}
