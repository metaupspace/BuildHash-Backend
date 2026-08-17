package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ProductListResponse")
public record ProductListResponse(
        List<ProductListItemResponse> items,
        @Schema(description = "Pass as ?cursor= to fetch the next page; null when this is the last page")
        String nextCursor
) {
}
