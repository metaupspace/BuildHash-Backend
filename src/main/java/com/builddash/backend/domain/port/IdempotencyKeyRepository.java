package com.builddash.backend.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository {
    Optional<UUID> findOrderId(String key);
    void save(String key, UUID orderId);
}
