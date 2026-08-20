package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.WishlistEntry;
import com.builddash.backend.infra.persistence.entity.WishlistEntryEntity;
import com.builddash.backend.infra.persistence.mapper.WishlistEntryMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WishlistEntryMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        WishlistEntry original = new WishlistEntry();
        original.setId(UUID.randomUUID());
        original.setUserId(UUID.randomUUID());
        original.setProductId(UUID.randomUUID());
        original.setCreatedAt(Instant.now());

        WishlistEntryEntity entity = WishlistEntryMapper.toEntity(original);
        WishlistEntry roundTripped = WishlistEntryMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getProductId()).isEqualTo(original.getProductId());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
