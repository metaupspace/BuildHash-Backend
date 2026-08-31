package com.builddash.backend.application.service;

import java.util.UUID;

/**
 * PO import conversion (PO_CONVERT, company-scoped — site selection is
 * deferred from 9-C). Marks the import CONVERTED and exposes its B2B_DRAFT
 * cart id; the cart enters the existing checkout flow later. No order, no
 * payment, no approval.
 */
public interface PoConversionService {

    /** Idempotent: an already-CONVERTED import returns its existing draft cart id. */
    UUID convert(UUID userId, UUID importId);
}
