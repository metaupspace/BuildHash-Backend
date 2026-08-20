package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Review;
import com.builddash.backend.infra.persistence.entity.ReviewEntity;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static Review toDomain(ReviewEntity entity) {
        Review review = new Review();
        review.setId(entity.getId());
        review.setProductId(entity.getProductId());
        review.setUserId(entity.getUserId());
        review.setRating(entity.getRating());
        review.setComment(entity.getComment());
        review.setStatus(entity.getStatus());
        review.setVerifiedPurchase(entity.isVerifiedPurchase());
        review.setCreatedAt(entity.getCreatedAt());
        return review;
    }

    public static ReviewEntity toEntity(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.setId(review.getId());
        entity.setProductId(review.getProductId());
        entity.setUserId(review.getUserId());
        entity.setRating(review.getRating());
        entity.setComment(review.getComment());
        entity.setStatus(review.getStatus());
        entity.setVerifiedPurchase(review.isVerifiedPurchase());
        entity.setCreatedAt(review.getCreatedAt());
        return entity;
    }
}
