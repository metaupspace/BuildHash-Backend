package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "ImageSearchResponse")
public record ImageSearchResponse(List<UUID> productIds) {
}
