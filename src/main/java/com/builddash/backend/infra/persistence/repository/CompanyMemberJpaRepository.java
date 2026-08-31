package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanyMemberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyMemberJpaRepository extends JpaRepository<CompanyMemberEntity, UUID> {

    Optional<CompanyMemberEntity> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    List<CompanyMemberEntity> findByCompanyId(UUID companyId);

    /**
     * Step 2 of the last-admin lock protocol: locks the complete member set of a
     * company in one query. ORDER BY id gives every caller the same deterministic
     * row order, so two concurrent mutations cannot deadlock between member rows —
     * they queue on the company-row lock acquired first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM CompanyMemberEntity m WHERE m.companyId = :companyId ORDER BY m.id")
    List<CompanyMemberEntity> findByCompanyIdForUpdate(@Param("companyId") UUID companyId);

    List<CompanyMemberEntity> findByUserId(UUID userId);
}
