package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ReviewReader;
import com.builddash.backend.application.service.ReviewWriter;
import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Review;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ReviewServiceImpl implements ReviewReader, ReviewWriter {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;


    @Override
    public List<Review> listApproved(UUID productId) {
        return listApproved(productId, 0, 20);
    }

    @Override
    public List<Review> listApproved(UUID productId, int page, int size) {
        return reviewRepository.findByProductIdAndStatus(productId, ModerationStatus.APPROVED, page, size);
    }

    /**
     * Verified-purchase check (join against completed orders for this SKU+user) is stubbed —
     * no Order module exists yet. Every submission is accepted; Review.verifiedPurchase stays
     * false until an Orders module lands. See PROGRESS.md Wave 2.
     */
    @Override
    @Transactional
    public Review submit(UUID productId, UUID userId, int rating, String comment) {
        productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId));

        Review review = new Review();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setComment(comment);
        return reviewRepository.save(review);
    }
}
