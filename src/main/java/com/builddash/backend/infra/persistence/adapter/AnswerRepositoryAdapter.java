package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.port.AnswerRepository;
import com.builddash.backend.infra.persistence.mapper.AnswerMapper;
import com.builddash.backend.infra.persistence.repository.AnswerJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class AnswerRepositoryAdapter implements AnswerRepository {

    private final AnswerJpaRepository jpaRepository;


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

    @Override
    public java.util.List<Answer> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(AnswerMapper::toDomain)
                .toList();
    }
}
