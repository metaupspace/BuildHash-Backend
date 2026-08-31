package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * B2B company account. Names are intentionally not unique (real-world duplicates are
 * legal); identity is the id. business_timezone drives monthly statement period
 * boundaries (9-E). gst_number/statement_email are used by invoicing/statements.
 */
public record Company(
        UUID id,
        String name,
        String gstNumber,
        String statementEmail,
        String businessTimezone,
        CompanyStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public Company suspend() {
        return new Company(id, name, gstNumber, statementEmail, businessTimezone,
                CompanyStatus.SUSPENDED, createdAt, updatedAt);
    }

    public Company activate() {
        return new Company(id, name, gstNumber, statementEmail, businessTimezone,
                CompanyStatus.ACTIVE, createdAt, updatedAt);
    }
}
