package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.WishlistEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository {

    WishlistEntry save(WishlistEntry entry);

    List<WishlistEntry> findByUserId(UUID userId);

    Optional<WishlistEntry> findByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)): all the user's wishlist entries in one statement. */
    void deleteByUserId(UUID userId);
}
