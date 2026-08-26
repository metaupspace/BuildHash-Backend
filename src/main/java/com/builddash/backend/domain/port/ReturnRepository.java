package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Return;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRepository {
    Return save(Return returnAggregate);
    Optional<Return> findById(UUID id);
    Optional<Return> findByOrderId(UUID orderId);
    List<Return> findAllByUserId(UUID userId);
}
