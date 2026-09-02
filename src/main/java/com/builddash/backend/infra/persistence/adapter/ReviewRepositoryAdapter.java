package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.model.Review;
import com.builddash.backend.domain.port.ReviewRepository;
import com.builddash.backend.infra.persistence.mapper.ReviewMapper;
import com.builddash.backend.infra.persistence.repository.ReviewJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class ReviewRepositoryAdapter implements ReviewRepository {

    private final ReviewJpaRepository jpaRepository;


    @Override
    public Review save(Review review) {
        return ReviewMapper.toDomain(jpaRepository.save(ReviewMapper.toEntity(review)));
    }

    @Override
    public List<Review> findByProductIdAndStatus(UUID productId, ModerationStatus status) {
        return findByProductIdAndStatus(productId, status, 0, 20);
    }

    @Override
    public List<Review> findByProductIdAndStatus(UUID productId, ModerationStatus status, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        return jpaRepository.findByProductIdAndStatus(productId, status, org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize))
                .stream()
                .map(ReviewMapper::toDomain)
                .toList();
    }

    @Override
    public java.util.List<Review> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(ReviewMapper::toDomain)
                .toList();
    }
}
