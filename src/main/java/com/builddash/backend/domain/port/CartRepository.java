package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Cart;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    Optional<Cart> findByUserIdAndProjectId(UUID userId, UUID projectId);
    Optional<Cart> findById(UUID id);
    Cart save(Cart cart);
    void delete(UUID id);
}
