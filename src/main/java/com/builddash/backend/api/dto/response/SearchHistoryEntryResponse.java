package com.builddash.backend.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "SearchHistoryEntryResponse")
public record SearchHistoryEntryResponse(String queryText, Instant createdAt) {
}
