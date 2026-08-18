package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.model.Review;
import com.builddash.backend.domain.port.ReviewRepository;
import com.builddash.backend.infra.persistence.mapper.ReviewMapper;
import com.builddash.backend.infra.persistence.repository.ReviewJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class ReviewRepositoryAdapter implements ReviewRepository {

    private final ReviewJpaRepository jpaRepository;

    ReviewRepositoryAdapter(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Review save(Review review) {
        return ReviewMapper.toDomain(jpaRepository.save(ReviewMapper.toEntity(review)));
    }

    @Override
    public List<Review> findByProductIdAndStatus(UUID productId, ModerationStatus status) {
        return jpaRepository.findByProductIdAndStatus(productId, status).stream()
                .map(ReviewMapper::toDomain)
                .toList();
    }
}
