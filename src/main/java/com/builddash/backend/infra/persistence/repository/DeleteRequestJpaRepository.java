package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.DeleteRequestStatus;
import com.builddash.backend.infra.persistence.entity.DeleteRequestEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeleteRequestJpaRepository extends JpaRepository<DeleteRequestEntity, UUID> {

    Optional<DeleteRequestEntity> findByUserIdAndStatus(UUID userId, DeleteRequestStatus status);

    List<DeleteRequestEntity> findByStatusAndDeletionScheduledAtLessThanEqual(DeleteRequestStatus status, Instant cutoff);

    @Query("SELECT r FROM DeleteRequestEntity r WHERE r.status = :status AND r.deletionScheduledAt <= :cutoff ORDER BY r.deletionScheduledAt ASC")
    List<DeleteRequestEntity> findDuePaged(@Param("status") DeleteRequestStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);
}
