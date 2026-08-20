package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Question;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        Question original = new Question();
        original.setId(UUID.randomUUID());
        original.setProductId(UUID.randomUUID());
        original.setUserId(UUID.randomUUID());
        original.setBody("Is this waterproof?");
        original.setCreatedAt(Instant.now());

        QuestionEntity entity = QuestionMapper.toEntity(original);
        Question roundTripped = QuestionMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getProductId()).isEqualTo(original.getProductId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getBody()).isEqualTo(original.getBody());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
