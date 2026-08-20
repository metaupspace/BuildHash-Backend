package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.WishlistEntry;
import com.builddash.backend.infra.persistence.entity.WishlistEntryEntity;

public final class WishlistEntryMapper {

    private WishlistEntryMapper() {
    }

    public static WishlistEntry toDomain(WishlistEntryEntity entity) {
        WishlistEntry entry = new WishlistEntry();
        entry.setId(entity.getId());
        entry.setUserId(entity.getUserId());
        entry.setProductId(entity.getProductId());
        entry.setCreatedAt(entity.getCreatedAt());
        return entry;
    }

    public static WishlistEntryEntity toEntity(WishlistEntry entry) {
        WishlistEntryEntity entity = new WishlistEntryEntity();
        entity.setId(entry.getId());
        entity.setUserId(entry.getUserId());
        entity.setProductId(entry.getProductId());
        entity.setCreatedAt(entry.getCreatedAt());
        return entity;
    }
}
