package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.model.Review;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        Review original = new Review();
        original.setId(UUID.randomUUID());
        original.setProductId(UUID.randomUUID());
        original.setUserId(UUID.randomUUID());
        original.setRating(4);
        original.setComment("Good product");
        original.setStatus(ModerationStatus.PENDING);
        original.setVerifiedPurchase(true);
        original.setCreatedAt(Instant.now());

        ReviewEntity entity = ReviewMapper.toEntity(original);
        Review roundTripped = ReviewMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getProductId()).isEqualTo(original.getProductId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getRating()).isEqualTo(original.getRating());
        assertThat(roundTripped.getComment()).isEqualTo(original.getComment());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.isVerifiedPurchase()).isEqualTo(original.isVerifiedPurchase());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
