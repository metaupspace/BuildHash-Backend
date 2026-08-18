package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.ProductImage;
import com.builddash.backend.domain.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(name = "ProductDetailResponse")
public record ProductDetailResponse(
        String id,
        String name,
        String slug,
        String categoryId,
        String categoryName,
        String brand,
        String hsnCode,
        @Schema(description = "Null if the HSN code has no matching Postgres master-data row")
        BigDecimal gstRatePercent,
        Map<String, Object> attributes,
        List<ProductImage> images,
        @Schema(example = "in_stock", allowableValues = {"in_stock", "out_of_stock"})
        String stockStatus,
        ProductStatus status
) {
}
