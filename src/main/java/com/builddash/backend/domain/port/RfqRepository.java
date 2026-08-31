package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Rfq;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RfqRepository {

    Rfq save(Rfq rfq);

    Optional<Rfq> findById(UUID id);

    /** Pessimistic row lock — the serialization point for quote submission, cancel and convert. */
    Optional<Rfq> findByIdForUpdate(UUID id);

    /**
     * Sweeper operation: one conditional UPDATE (status OPEN AND expires_at <= now
     * -> EXPIRED), no per-row loop. Returns the number of RFQs expired so far this
     * sweep. Multiple scheduler instances are safe: the UPDATE is atomic and the
     * WHERE clause re-evaluates against the locked row version.
     */
    int expireOpenBefore(Instant now);
}
