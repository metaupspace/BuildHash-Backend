package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Refund;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {
    Refund save(Refund refund);
    Optional<Refund> findById(UUID id);
    Optional<Refund> findByReturnId(UUID returnId);
    Optional<Refund> findLatestByReturnId(UUID returnId);
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
    List<Refund> findAllByReturnId(UUID returnId);

    /** Row lock for the webhook/finalize race (H1.1/H1.4b): acquire after locking the
     *  owning Return row (canonical order RETURN -> REFUND) so both writers serialize. */
    Optional<Refund> findByIdForUpdate(UUID id);
}
