package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.ReviewResponse;
import com.builddash.backend.domain.model.Review;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(review.getId(), review.getProductId(), review.getUserId(),
                review.getRating(), review.getComment(), review.getCreatedAt());
    }

    public List<ReviewResponse> toResponseList(List<Review> reviews) {
        return reviews.stream().map(this::toResponse).toList();
    }
}
