package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanyEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {

    /**
     * Step 1 of the last-admin lock protocol: the per-company serialization entry
     * point. Every membership mutation that can affect the OWNER/ADMIN invariant
     * locks this row before touching member rows (GstSequenceJpaRepository pattern).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompanyEntity c WHERE c.id = :id")
    Optional<CompanyEntity> findByIdForUpdate(@Param("id") UUID id);
}
