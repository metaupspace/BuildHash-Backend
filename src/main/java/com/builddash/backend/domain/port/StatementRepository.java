package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Statement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementRepository {

    Statement save(Statement statement);

    Optional<Statement> findById(UUID id);

    /** Pessimistic row lock — the Tx2 finalize path. */
    Optional<Statement> findByIdForUpdate(UUID id);

    Optional<Statement> findLatestByCompanyAndPeriod(UUID companyId, Instant periodStart);

    /** All generations for a company, newest period first, version descending within a period. */
    List<Statement> findByCompanyIdOrderByPeriodStartDescVersionDesc(UUID companyId);

    /** Period keys (any status) already claimed for a company — the due-period filter input. */
    List<String> findClaimedPeriodKeys(UUID companyId);

    /** Scheduler claim query, invoice shape: PENDING under the attempt cap, or stale GENERATING. */
    List<UUID> findSchedulerClaimableStatements(int maxAttempts, Instant staleCutoff);

    /** DLQ recovery shape: DLQ_RETRY, or GENERATING at/over the cap gone stale. */
    List<UUID> findDlqClaimableStatements(int maxAttempts, Instant staleCutoff);

    /** Email sweep input: READY with delivery outstanding and attempts left. */
    List<UUID> findReadyStatementsAwaitingEmail(int maxEmailAttempts, int limit);
}
