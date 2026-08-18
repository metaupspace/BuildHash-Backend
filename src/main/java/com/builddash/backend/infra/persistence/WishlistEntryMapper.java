package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.WishlistEntry;

final class WishlistEntryMapper {

    private WishlistEntryMapper() {
    }

    static WishlistEntry toDomain(WishlistEntryEntity entity) {
        WishlistEntry entry = new WishlistEntry();
        entry.setId(entity.getId());
        entry.setUserId(entity.getUserId());
        entry.setProductId(entity.getProductId());
        entry.setCreatedAt(entity.getCreatedAt());
        return entry;
    }

    static WishlistEntryEntity toEntity(WishlistEntry entry) {
        WishlistEntryEntity entity = new WishlistEntryEntity();
        entity.setId(entry.getId());
        entity.setUserId(entry.getUserId());
        entity.setProductId(entry.getProductId());
        entity.setCreatedAt(entry.getCreatedAt());
        return entity;
    }
}
