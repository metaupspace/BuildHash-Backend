package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.port.QuestionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class QuestionRepositoryAdapter implements QuestionRepository {

    private final QuestionJpaRepository jpaRepository;

    QuestionRepositoryAdapter(QuestionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Question save(Question question) {
        return QuestionMapper.toDomain(jpaRepository.save(QuestionMapper.toEntity(question)));
    }

    @Override
    public List<Question> findByProductId(UUID productId) {
        return jpaRepository.findByProductId(productId).stream()
                .map(QuestionMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Question> findById(UUID id) {
        return jpaRepository.findById(id).map(QuestionMapper::toDomain);
    }
}
