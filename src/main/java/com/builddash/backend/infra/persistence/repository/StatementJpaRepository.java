package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.StatementEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementJpaRepository extends JpaRepository<StatementEntity, UUID> {

    Optional<StatementEntity> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StatementEntity s WHERE s.id = :id")
    Optional<StatementEntity> findByIdForUpdate(@Param("id") UUID id);

    /** Any-status generations for the period (start boundary identifies it). */
    List<StatementEntity> findByCompanyIdAndPeriodStartOrderByVersionDesc(UUID companyId, Instant periodStart);

    List<StatementEntity> findByCompanyIdOrderByPeriodStartDescVersionDesc(UUID companyId);

    @Query("SELECT s.periodKey FROM StatementEntity s WHERE s.companyId = :companyId")
    List<String> findClaimedPeriodKeys(@Param("companyId") UUID companyId);

    /** Invoice scheduler shape: PENDING under the cap, or GENERATING gone stale. */
    @Query("SELECT s.id FROM StatementEntity s WHERE (s.status = 'PENDING' AND s.attemptCount < :maxAttempts) "
            + "OR (s.status = 'GENERATING' AND s.attemptCount < :maxAttempts AND s.updatedAt < :staleCutoff)")
    List<UUID> findSchedulerClaimableStatements(@Param("maxAttempts") int maxAttempts,
                                                @Param("staleCutoff") Instant staleCutoff);

    @Query("SELECT s.id FROM StatementEntity s WHERE s.status = 'DLQ_RETRY' "
            + "OR (s.status = 'GENERATING' AND s.attemptCount >= :maxAttempts AND s.updatedAt < :staleCutoff)")
    List<UUID> findDlqClaimableStatements(@Param("maxAttempts") int maxAttempts,
                                          @Param("staleCutoff") Instant staleCutoff);

    @Query("SELECT s.id FROM StatementEntity s WHERE s.status = 'READY' "
            + "AND s.emailStatus IN ('NONE', 'FAILED') AND s.emailAttemptCount < :maxEmailAttempts "
            + "ORDER BY s.generatedAt ASC")
    List<UUID> findReadyStatementsAwaitingEmail(@Param("maxEmailAttempts") int maxEmailAttempts,
                                                org.springframework.data.domain.Pageable pageable);
}
