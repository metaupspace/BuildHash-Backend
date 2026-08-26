package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.infra.persistence.entity.GstNoteEntity;
import com.builddash.backend.infra.persistence.mapper.GstNoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GstNoteMapperTest {

    private GstNoteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GstNoteMapper();
    }

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        UUID id = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        String number = "CRN-2627-000001";
        BigDecimal amount = new BigDecimal("750.50");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        GstNote original = new GstNote(
                id,
                returnId,
                GstNoteType.CREDIT,
                number,
                amount,
                now,
                now,
                now
        );

        GstNoteEntity entity = mapper.toEntity(original);
        GstNote roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.id()).isEqualTo(id);
        assertThat(roundTripped.returnId()).isEqualTo(returnId);
        assertThat(roundTripped.noteType()).isEqualTo(GstNoteType.CREDIT);
        assertThat(roundTripped.number()).isEqualTo(number);
        assertThat(roundTripped.amount()).isEqualByComparingTo(amount);
        assertThat(roundTripped.generatedAt()).isEqualTo(now);
        assertThat(roundTripped.createdAt()).isEqualTo(now);
        assertThat(roundTripped.updatedAt()).isEqualTo(now);
    }

    @Test
    void nullMappings_returnNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
