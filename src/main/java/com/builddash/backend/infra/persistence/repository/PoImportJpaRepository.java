package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.PoImportEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoImportJpaRepository extends JpaRepository<PoImportEntity, UUID> {

    Optional<PoImportEntity> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);

    /** Conversion serialization point (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PoImportEntity i WHERE i.id = :id")
    Optional<PoImportEntity> findByIdForUpdate(@Param("id") UUID id);
}
