package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanySiteEntity;
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
public interface CompanySiteJpaRepository extends JpaRepository<CompanySiteEntity, UUID> {

    List<CompanySiteEntity> findByCompanyId(UUID companyId);

    /**
     * Shared serialization point between site deactivation (9-A) and the future
     * order-site association flows (9-B/9-C): whichever path takes this lock second
     * waits, so "deactivate while an order associates the site" cannot interleave.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CompanySiteEntity s WHERE s.id = :id")
    Optional<CompanySiteEntity> findByIdForUpdate(@Param("id") UUID id);
}
