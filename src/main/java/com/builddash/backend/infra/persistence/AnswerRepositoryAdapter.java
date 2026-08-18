package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.port.AnswerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class AnswerRepositoryAdapter implements AnswerRepository {

    private final AnswerJpaRepository jpaRepository;

    AnswerRepositoryAdapter(AnswerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Answer save(Answer answer) {
        return AnswerMapper.toDomain(jpaRepository.save(AnswerMapper.toEntity(answer)));
    }

    @Override
    public List<Answer> findByQuestionIdIn(List<UUID> questionIds) {
        return jpaRepository.findByQuestionIdIn(questionIds).stream()
                .map(AnswerMapper::toDomain)
                .toList();
    }
}
