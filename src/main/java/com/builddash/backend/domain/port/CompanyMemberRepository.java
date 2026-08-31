package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.CompanyMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMemberRepository {

    CompanyMember save(CompanyMember member);

    Optional<CompanyMember> findById(UUID id);

    Optional<CompanyMember> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    List<CompanyMember> findByCompanyId(UUID companyId);

    /**
     * Locks ALL member rows of a company in one query (ordered by primary key) —
     * step 2 of the last-admin protocol, always acquired after findByIdForUpdate
     * on the company row. The invariant is then evaluated over the complete locked
     * member set inside the mutating transaction.
     */
    List<CompanyMember> findByCompanyIdForUpdate(UUID companyId);

    List<CompanyMember> findByUserId(UUID userId);

    void deleteById(UUID id);
}
