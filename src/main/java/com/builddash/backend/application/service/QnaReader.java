package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.QuestionThread;

import java.util.List;
import java.util.UUID;

public interface QnaReader {

    List<QuestionThread> listThreads(UUID productId);

    List<QuestionThread> listThreads(UUID productId, int page, int size);
}
