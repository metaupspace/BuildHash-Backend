package com.builddash.backend.domain.exception;

import java.time.Instant;
import java.util.UUID;

/** Thrown on a second deletion request while one is still pending (409 DELETE_REQUEST_PENDING). */
public class DeleteRequestPendingException extends DomainException {

    public DeleteRequestPendingException(UUID userId, Instant deletionScheduledAt) {
        super("DELETE_REQUEST_PENDING",
                "A deletion request is already pending for this account, scheduled at " + deletionScheduledAt);
    }
}
