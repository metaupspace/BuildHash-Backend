package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.AnswerSource;
import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.model.Answer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        Answer original = new Answer();
        original.setId(UUID.randomUUID());
        original.setQuestionId(UUID.randomUUID());
        original.setUserId(UUID.randomUUID());
        original.setBody("Yes it is.");
        original.setSource(AnswerSource.VENDOR);
        original.setStatus(ModerationStatus.APPROVED);
        original.setCreatedAt(Instant.now());

        AnswerEntity entity = AnswerMapper.toEntity(original);
        Answer roundTripped = AnswerMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getQuestionId()).isEqualTo(original.getQuestionId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getBody()).isEqualTo(original.getBody());
        assertThat(roundTripped.getSource()).isEqualTo(original.getSource());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }
}
