package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Company;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);

    /**
     * Pessimistic-lock entry point of the last-admin protocol: every membership
     * mutation that can affect the OWNER/ADMIN invariant locks the company row first,
     * then the member rows — a fixed order shared by all such mutations so concurrent
     * mutations serialize here instead of deadlocking.
     */
    Company findByIdForUpdate(UUID id);

    void deleteById(UUID id);
}
