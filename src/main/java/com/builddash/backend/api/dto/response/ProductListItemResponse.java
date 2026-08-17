package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProductListItemResponse")
public record ProductListItemResponse(
        String id,
        @Schema(example = "UltraTech Cement OPC 53 Grade 50kg") String name,
        String slug,
        @Schema(example = "UltraTech") String brand,
        String categoryId,
        String primaryImageUrl
) {
}
