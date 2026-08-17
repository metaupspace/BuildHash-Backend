package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enriched read-model for the product detail view — combines Product with data resolved from
 * other aggregates (category name, GST rate) and a derived projection (inStock). Not a
 * persisted entity itself, but still domain data: assembling it is exactly what getDetail()'s
 * use case means.
 */
public record ProductDetail(
        UUID id,
        String name,
        String slug,
        UUID categoryId,
        String categoryName,
        String brand,
        String hsnCode,
        BigDecimal gstRatePercent,
        Map<String, Object> attributes,
        List<ProductImage> images,
        boolean inStock,
        ProductStatus status
) {
}
