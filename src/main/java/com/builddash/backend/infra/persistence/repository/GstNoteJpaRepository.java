package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.GstNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GstNoteJpaRepository extends JpaRepository<GstNoteEntity, UUID> {
    Optional<GstNoteEntity> findByReturnId(UUID returnId);
    Optional<GstNoteEntity> findByNumber(String number);
    List<GstNoteEntity> findAllByReturnIdOrderByCreatedAtDesc(UUID returnId);
}
