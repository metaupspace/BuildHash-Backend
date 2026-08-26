package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.infra.persistence.mapper.GstNoteMapper;
import com.builddash.backend.infra.persistence.repository.GstNoteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class GstNoteRepositoryAdapter implements GstNoteRepository {

    private final GstNoteJpaRepository jpaRepository;
    private final GstNoteMapper mapper;

    @Override
    public GstNote save(GstNote note) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(note)));
    }

    @Override
    public Optional<GstNote> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<GstNote> findByReturnId(UUID returnId) {
        return jpaRepository.findByReturnId(returnId).map(mapper::toDomain);
    }

    @Override
    public Optional<GstNote> findByNumber(String number) {
        return jpaRepository.findByNumber(number).map(mapper::toDomain);
    }

    @Override
    public List<GstNote> findAllByReturnId(UUID returnId) {
        return jpaRepository.findAllByReturnIdOrderByCreatedAtDesc(returnId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
