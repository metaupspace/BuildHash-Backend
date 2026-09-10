package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.GstSequenceType;

import java.time.Instant;

/**
 * Statutory document-number allocation (invoices, credit notes, debit notes), scoped per
 * sequence type per Indian fiscal year (Apr–Mar IST). Implementations must allocate
 * atomically with the caller's transaction so a rolled-back document never consumes a
 * number.
 */
public interface GstSequenceRepository {

    String nextNumber(GstSequenceType sequenceType);

    String nextNumber(GstSequenceType sequenceType, Instant asOf);
}
