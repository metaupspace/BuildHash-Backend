package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.SearchQueryLogEntry;
import com.builddash.backend.infra.persistence.entity.SearchQueryLogEntity;

public final class SearchQueryLogMapper {

    private SearchQueryLogMapper() {
    }

    public static SearchQueryLogEntry toDomain(SearchQueryLogEntity entity) {
        SearchQueryLogEntry entry = new SearchQueryLogEntry();
        entry.setId(entity.getId());
        entry.setUserId(entity.getUserId());
        entry.setQueryText(entity.getQueryText());
        entry.setLang(entity.getLang());
        entry.setCreatedAt(entity.getCreatedAt());
        return entry;
    }

    public static SearchQueryLogEntity toEntity(SearchQueryLogEntry entry) {
        SearchQueryLogEntity entity = new SearchQueryLogEntity();
        entity.setId(entry.getId());
        entity.setUserId(entry.getUserId());
        entity.setQueryText(entry.getQueryText());
        entity.setLang(entry.getLang());
        entity.setCreatedAt(entry.getCreatedAt());
        return entity;
    }
}
