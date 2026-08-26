package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.GstNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GstNoteRepository {
    GstNote save(GstNote note);
    Optional<GstNote> findById(UUID id);
    Optional<GstNote> findByReturnId(UUID returnId);
    Optional<GstNote> findByNumber(String number);
    List<GstNote> findAllByReturnId(UUID returnId);
}
