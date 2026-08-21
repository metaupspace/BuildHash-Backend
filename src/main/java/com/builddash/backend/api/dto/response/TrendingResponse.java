package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TrendingResponse")
public record TrendingResponse(List<String> queries) {
}
