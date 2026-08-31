package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.PoImport;

import java.util.Optional;
import java.util.UUID;

public interface PoImportRepository {

    PoImport save(PoImport poImport);

    Optional<PoImport> findById(UUID id);

    /** Row lock — the serialization point for conversion. */
    Optional<PoImport> findByIdForUpdate(UUID id);

    Optional<PoImport> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);
}
