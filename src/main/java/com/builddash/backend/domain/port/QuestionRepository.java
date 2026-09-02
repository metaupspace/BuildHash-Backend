package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Question save(Question question);

    List<Question> findByProductId(UUID productId);

    List<Question> findByProductId(UUID productId, int page, int size);

    Optional<Question> findById(UUID id);

    /** DPDP export: every question the user authored. */
    List<Question> findAllByUserId(UUID userId);
}
