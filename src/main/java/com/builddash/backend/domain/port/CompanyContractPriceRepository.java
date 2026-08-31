package com.builddash.backend.domain.port;

import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.model.CompanyContractPrice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CompanyContractPriceRepository {

    Optional<CompanyContractPrice> findActive(UUID companyId, UUID productId, Instant asOf);

    /**
     * Rejects with ContractPriceOverlapException if the window overlaps an existing row
     * for the same company+product (application check + GiST exclusion backstop, same
     * double enforcement as ContractPriceRepositoryAdapter).
     */
    CompanyContractPrice save(CompanyContractPrice price);
}
