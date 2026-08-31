package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.CompanySite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanySiteRepository {

    CompanySite save(CompanySite site);

    Optional<CompanySite> findById(UUID id);

    /** Site-row lock shared by deactivation (9-A) and future order-site association (9-B/9-C). */
    CompanySite findByIdForUpdate(UUID id);

    List<CompanySite> findByCompanyId(UUID companyId);

    void deleteById(UUID id);
}
