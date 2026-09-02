package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Review;

import java.util.List;
import java.util.UUID;

/**
 * ISP: ReviewController's GET handler only ever reads, never writes.
 */
public interface ReviewReader {

    List<Review> listApproved(UUID productId);

    List<Review> listApproved(UUID productId, int page, int size);
}
