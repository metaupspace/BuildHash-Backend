package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ContractPrice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContractPriceRepository {

    Optional<ContractPrice> findActive(UUID userId, UUID productId, Instant asOf);

    /**
     * Rejects with ContractPriceOverlapException if the given window overlaps an
     * existing row for the same user+product — see ContractPriceRepositoryAdapter.
     */
    ContractPrice save(ContractPrice contractPrice);
}
