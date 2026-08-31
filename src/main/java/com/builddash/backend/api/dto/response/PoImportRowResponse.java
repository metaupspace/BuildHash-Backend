package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.PoImportRow;

/** One source row's outcome. The XLSX column "sku" maps to productSlug (catalog products.slug). */
public record PoImportRowResponse(
        int rowIndex,
        String productSlug,
        Integer quantity,
        String status,
        String errorCode
) {

    public static PoImportRowResponse from(PoImportRow row) {
        return new PoImportRowResponse(row.rowIndex(), row.productSlug(), row.quantity(),
                row.status().name(), row.errorCode());
    }
}
