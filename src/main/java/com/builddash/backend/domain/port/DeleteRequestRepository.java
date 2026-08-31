package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.DeleteRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeleteRequestRepository {

    DeleteRequest save(DeleteRequest request);

    /** The pending request for a user, if one exists — the service-level 409 fast path. */
    Optional<DeleteRequest> findPendingByUserId(UUID userId);

    /** Due = PENDING and deletionScheduledAt <= now. */
    List<DeleteRequest> findDue(Instant now);
}
