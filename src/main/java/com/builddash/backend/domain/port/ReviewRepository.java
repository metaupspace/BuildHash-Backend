package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.model.Review;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    List<Review> findByProductIdAndStatus(UUID productId, ModerationStatus status);

    /** DPDP export: every review the user authored, regardless of moderation status. */
    List<Review> findAllByUserId(UUID userId);
}
