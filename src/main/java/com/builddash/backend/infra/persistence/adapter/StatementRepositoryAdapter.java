package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.StatementDiscrepancyType;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementDiscrepancy;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.infra.persistence.entity.StatementEntity;
import com.builddash.backend.infra.persistence.repository.StatementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class StatementRepositoryAdapter implements StatementRepository {

    private final StatementJpaRepository jpaRepository;

    @Override
    public Statement save(Statement statement) {
        StatementEntity entity = jpaRepository.findById(statement.id())
                .orElseGet(() -> {
                    StatementEntity e = new StatementEntity();
                    e.setId(statement.id());
                    return e;
                });
        entity.setCompanyId(statement.companyId());
        entity.setPeriodStart(statement.periodStart());
        entity.setPeriodEnd(statement.periodEnd());
        entity.setPeriodKey(statement.periodKey());
        entity.setStatus(statement.status().name());
        entity.setVersion(statement.version());
        entity.setStatementNumber(statement.statementNumber());
        entity.setPdfStorageKey(statement.pdfStorageKey());
        entity.setXlsxStorageKey(statement.xlsxStorageKey());
        entity.setPdfSizeBytes(statement.pdfSizeBytes());
        entity.setXlsxSizeBytes(statement.xlsxSizeBytes());
        entity.setGeneratedAt(statement.generatedAt());
        entity.setAttemptCount(statement.attemptCount());
        entity.setEmailStatus(statement.emailStatus().name());
        entity.setEmailedAt(statement.emailedAt());
        entity.setEmailAttemptCount(statement.emailAttemptCount());
        entity.setOrderCount(statement.orderCount());
        entity.setGrossTotal(statement.grossTotal());
        entity.setTaxTotal(statement.taxTotal());
        entity.setNetTotal(statement.netTotal());
        entity.setCreditTotal(statement.creditTotal());
        entity.setDueTotal(statement.dueTotal());
        entity.setDiscrepanciesJson(toJson(statement.discrepancies()));
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Statement> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Statement> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<Statement> findLatestByCompanyAndPeriod(UUID companyId, Instant periodStart) {
        return jpaRepository.findByCompanyIdAndPeriodStartOrderByVersionDesc(companyId, periodStart)
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<Statement> findByCompanyIdOrderByPeriodStartDescVersionDesc(UUID companyId) {
        return jpaRepository.findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<String> findClaimedPeriodKeys(UUID companyId) {
        return jpaRepository.findClaimedPeriodKeys(companyId);
    }

    @Override
    public List<UUID> findSchedulerClaimableStatements(int maxAttempts, Instant staleCutoff) {
        return jpaRepository.findSchedulerClaimableStatements(maxAttempts, staleCutoff);
    }

    @Override
    public List<UUID> findDlqClaimableStatements(int maxAttempts, Instant staleCutoff) {
        return jpaRepository.findDlqClaimableStatements(maxAttempts, staleCutoff);
    }

    @Override
    public List<UUID> findReadyStatementsAwaitingEmail(int maxEmailAttempts, int limit) {
        return jpaRepository.findReadyStatementsAwaitingEmail(maxEmailAttempts, PageRequest.of(0, limit));
    }

    private Statement toDomain(StatementEntity e) {
        return new Statement(e.getId(), e.getCompanyId(), e.getPeriodStart(), e.getPeriodEnd(),
                e.getPeriodKey(), StatementStatus.valueOf(e.getStatus()), e.getVersion(),
                e.getStatementNumber(), e.getPdfStorageKey(), e.getXlsxStorageKey(),
                e.getPdfSizeBytes(), e.getXlsxSizeBytes(), e.getGeneratedAt(), e.getAttemptCount(),
                StatementEmailStatus.valueOf(e.getEmailStatus()), e.getEmailedAt(), e.getEmailAttemptCount(),
                e.getOrderCount(), e.getGrossTotal(), e.getTaxTotal(), e.getNetTotal(),
                e.getCreditTotal(), e.getDueTotal(), fromJson(e.getDiscrepanciesJson()),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * Discrepancies persist as a JSONB array of strings encoded TYPE|orderId|detail —
     * the JSON column mapping serializes the list itself. Detail is generated by 9-E
     * only (invoice status text) and never contains the '|' separator.
     */
    private List<String> toJson(List<StatementDiscrepancy> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return null;
        }
        return discrepancies.stream()
                .map(d -> d.type().name() + "|" + d.orderId() + "|" + (d.detail() == null ? "" : d.detail()))
                .toList();
    }

    private List<StatementDiscrepancy> fromJson(List<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> {
            String[] parts = row.split("\\|", 3);
            return new StatementDiscrepancy(StatementDiscrepancyType.valueOf(parts[0]),
                    UUID.fromString(parts[1]), parts.length > 2 ? parts[2] : null);
        }).toList();
    }
}
