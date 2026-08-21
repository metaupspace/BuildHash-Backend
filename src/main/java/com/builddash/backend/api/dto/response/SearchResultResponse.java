package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SearchResultResponse")
public record SearchResultResponse(List<ProductSearchHitResponse> items) {
}
