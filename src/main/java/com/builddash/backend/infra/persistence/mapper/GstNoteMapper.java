package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.infra.persistence.entity.GstNoteEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GstNoteMapper {

    public GstNoteEntity toEntity(GstNote domain) {
        if (domain == null) {
            return null;
        }

        GstNoteEntity entity = new GstNoteEntity();
        entity.setId(domain.id());
        entity.setReturnId(domain.returnId());
        entity.setNoteType(domain.noteType());
        entity.setNumber(domain.number());
        entity.setAmount(domain.amount());
        entity.setGeneratedAt(domain.generatedAt() != null ? domain.generatedAt() : Instant.now());

        Instant now = Instant.now();
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : now);
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : now);

        return entity;
    }

    public GstNote toDomain(GstNoteEntity entity) {
        if (entity == null) {
            return null;
        }

        return new GstNote(
                entity.getId(),
                entity.getReturnId(),
                entity.getNoteType(),
                entity.getNumber(),
                entity.getAmount(),
                entity.getGeneratedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
