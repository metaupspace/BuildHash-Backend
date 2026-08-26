package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Refund;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {
    Refund save(Refund refund);
    Optional<Refund> findById(UUID id);
    Optional<Refund> findByReturnId(UUID returnId);
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    List<Refund> findAllByReturnId(UUID returnId);
}
