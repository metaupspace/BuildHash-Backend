package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.port.QuestionRepository;
import com.builddash.backend.infra.persistence.mapper.QuestionMapper;
import com.builddash.backend.infra.persistence.repository.QuestionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class QuestionRepositoryAdapter implements QuestionRepository {

    private final QuestionJpaRepository jpaRepository;


    @Override
    public Question save(Question question) {
        return QuestionMapper.toDomain(jpaRepository.save(QuestionMapper.toEntity(question)));
    }

    @Override
    public List<Question> findByProductId(UUID productId) {
        return findByProductId(productId, 0, 20);
    }

    @Override
    public List<Question> findByProductId(UUID productId, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        return jpaRepository.findByProductId(productId, org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize)).stream()
                .map(QuestionMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Question> findById(UUID id) {
        return jpaRepository.findById(id).map(QuestionMapper::toDomain);
    }

    @Override
    public java.util.List<Question> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(QuestionMapper::toDomain)
                .toList();
    }
}
