package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Review;

import java.util.UUID;

/**
 * ISP: ReviewController's POST handler only ever writes, never reads back.
 */
public interface ReviewWriter {

    Review submit(UUID productId, UUID userId, int rating, String comment);
}
