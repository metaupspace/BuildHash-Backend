package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Answer;

import java.util.List;
import java.util.UUID;

public interface AnswerRepository {

    Answer save(Answer answer);

    /** Batch fetch across all of a product's questions in one query — never call per-question. */
    List<Answer> findByQuestionIdIn(List<UUID> questionIds);
}
