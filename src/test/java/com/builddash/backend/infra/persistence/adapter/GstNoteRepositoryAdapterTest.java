package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.infra.persistence.entity.GstNoteEntity;
import com.builddash.backend.infra.persistence.mapper.GstNoteMapper;
import com.builddash.backend.infra.persistence.repository.GstNoteJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GstNoteRepositoryAdapterTest {

    @Mock
    private GstNoteJpaRepository jpaRepository;

    private GstNoteMapper mapper;
    private GstNoteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new GstNoteMapper();
        adapter = new GstNoteRepositoryAdapter(jpaRepository, mapper);
    }

    private GstNote sampleNote() {
        return new GstNote(
                UUID.randomUUID(),
                UUID.randomUUID(),
                GstNoteType.CREDIT,
                "CRN-2627-000001",
                new BigDecimal("499.00"),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void save_delegatesToJpaAndMapper() {
        GstNote domain = sampleNote();
        GstNoteEntity entity = mapper.toEntity(domain);
        when(jpaRepository.save(any(GstNoteEntity.class))).thenReturn(entity);

        GstNote saved = adapter.save(domain);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(saved.number()).isEqualTo(domain.number());
        verify(jpaRepository).save(any(GstNoteEntity.class));
    }

    @Test
    void findById_whenFound_returnsMappedDomain() {
        GstNote domain = sampleNote();
        GstNoteEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findById(domain.id())).thenReturn(Optional.of(entity));

        Optional<GstNote> result = adapter.findById(domain.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(domain.id());
    }

    @Test
    void findByReturnId_whenFound_returnsMappedDomain() {
        GstNote domain = sampleNote();
        GstNoteEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByReturnId(domain.returnId())).thenReturn(Optional.of(entity));

        Optional<GstNote> result = adapter.findByReturnId(domain.returnId());

        assertThat(result).isPresent();
        assertThat(result.get().returnId()).isEqualTo(domain.returnId());
    }

    @Test
    void findByNumber_whenFound_returnsMappedDomain() {
        GstNote domain = sampleNote();
        GstNoteEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByNumber(domain.number())).thenReturn(Optional.of(entity));

        Optional<GstNote> result = adapter.findByNumber(domain.number());

        assertThat(result).isPresent();
        assertThat(result.get().number()).isEqualTo(domain.number());
    }

    @Test
    void findAllByReturnId_returnsMappedDomainList() {
        GstNote domain = sampleNote();
        GstNoteEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findAllByReturnIdOrderByCreatedAtDesc(domain.returnId())).thenReturn(List.of(entity));

        List<GstNote> results = adapter.findAllByReturnId(domain.returnId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).returnId()).isEqualTo(domain.returnId());
    }
}
