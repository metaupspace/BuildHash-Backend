package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * Thrown when a Return creation is attempted against an order that already has an active
 * (non-REJECTED) return — the client-retry/double-submit double-refund guard. Status
 * mapping lives in GlobalExceptionHandler, not here (house convention: domain exceptions
 * carry code+message only, never HTTP semantics).
 */
public class ReturnAlreadyExistsException extends DomainException {
    public ReturnAlreadyExistsException(UUID orderId) {
        super("RETURN_ALREADY_EXISTS", "An active return already exists for order " + orderId);
    }
}
