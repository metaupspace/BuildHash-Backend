package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.model.Question;

import java.util.List;
import java.util.UUID;

public interface QnaWriter {

    Question ask(UUID productId, UUID userId, String body);

    /** roles is the caller's JWT roles — AnswerSourceResolver decides vendor/staff/customer from it. */
    Answer answer(UUID questionId, UUID userId, String body, List<String> roles);
}
