package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository {

    Question save(Question question);

    List<Question> findByProductId(UUID productId);

    Optional<Question> findById(UUID id);
}
