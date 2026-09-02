package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.StatementGenerationService;
import com.builddash.backend.application.service.StatementSequenceService;
import com.builddash.backend.domain.enums.StatementDiscrepancyType;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementDiscrepancy;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.StatementAccountingRepository;
import com.builddash.backend.domain.port.StatementAccountingRepository.AccountingTotals;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.domain.port.StatementRenderer;
import com.builddash.backend.domain.port.StatementWorkbookWriter;
import com.builddash.backend.domain.service.StatementPeriodCalculator;
import com.builddash.backend.domain.service.StatementPeriodCalculator.Period;
import com.builddash.backend.infra.config.StatementProperties;
import com.builddash.backend.infra.persistence.entity.CompanyEntity;
import com.builddash.backend.infra.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Statement generation (9-E).
 *
 * Lock discipline: the claim transaction takes the COMPANY row first (global lock
 * order), then inserts/reclaims the generation row; UNIQUE(company_id, period_start,
 * period_end, version) is the multi-instance backstop, translated AFTER the
 * transaction boundary (9-C lesson). Finalize takes only the statement row + the
 * per-company sequence row. No database transaction is ever held across aggregation
 * reads, PDF/XLSX rendering, or storage.
 *
 * Company discovery reuses the JPA repository directly (GstSequenceService precedent)
 * so the CompanyRepository PORT stays untouched — 9-E owns no company changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementGenerationServiceImpl implements StatementGenerationService {

    private static final int ORDER_ROW_PAGE = 500;
    private static final int LINE_PAGE = 1000;
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StatementRepository statementRepository;
    private final StatementAccountingRepository accountingRepository;
    private final StatementRenderer statementRenderer;
    private final StatementWorkbookWriter workbookWriter;
    private final StatementSequenceService sequenceService;
    private final CompanyRepository companyRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final ObjectStorage objectStorage;
    private final StatementProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public int generateDue() {
        int budget = properties.getGeneration().getSweepBatchLimit();
        int started = 0;
        for (CompanyEntity company : companyJpaRepository.findAll()) {
            if (budget <= 0) {
                break;
            }
            if (company.getStatus() != com.builddash.backend.domain.enums.CompanyStatus.ACTIVE) {
                continue; // suspended companies generate nothing
            }
            ZoneId zone = ZoneId.of(company.getBusinessTimezone());
            for (YearMonth month : StatementPeriodCalculator.closedMonths(zone, Instant.now())) {
                if (budget <= 0) {
                    break;
                }
                Period period = StatementPeriodCalculator.period(month, zone);
                Optional<Statement> latest = statementRepository.findLatestByCompanyAndPeriod(
                        company.getId(), period.start());
                if (latest.isPresent() && (latest.get().status() == StatementStatus.READY
                        || isFreshGenerating(latest.get()))) {
                    continue; // done, or another instance owns the active generation
                }
                UUID claimed = startGeneration(company, period);
                if (claimed != null) {
                    started++;
                    budget--; // only real work consumes the pass budget — empty months are free
                    try {
                        process(claimed);
                    } catch (Exception e) {
                        log.warn("Statement generation failed for {}: {}", claimed, e.getMessage());
                        // handleFailure already ran inside process(); recovery picks it up.
                    }
                }
            }
        }
        return started;
    }

    @Override
    public int recoverStuck() {
        Instant staleCutoff = Instant.now().minus(Duration.ofMinutes(properties.getGeneration().getStaleMinutes()));
        int processed = 0;
        for (UUID id : statementRepository.findSchedulerClaimableStatements(
                properties.getGeneration().getMaxAttempts(), staleCutoff)) {
            try {
                if (process(id)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("Statement recovery failed for {}: {}", id, e.getMessage());
            }
        }
        for (UUID id : statementRepository.findDlqClaimableStatements(
                properties.getGeneration().getMaxAttempts(), staleCutoff)) {
            try {
                if (process(id)) {
                    processed++;
                }
            } catch (Exception e) {
                log.warn("Statement DLQ recovery failed for {}: {}", id, e.getMessage());
            }
        }
        return processed;
    }

    /**
     * Tx1 (claim): COMPANY row lock first, then insert a new GENERATING version — or
     * reclaim a claimable existing row. Null = another instance owns it / month empty /
     * already READY. The UNIQUE constraint is the final backstop, handled post-tx.
     */
    private UUID startGeneration(CompanyEntity company, Period period) {
        AccountingTotals totals = accountingRepository.aggregateTotals(company.getId(), period.start(), period.end());
        if (totals.orderCount() == 0 && totals.creditNoteCount() == 0) {
            return null; // empty month: no row, no number, no email (locked decision 2)
        }
        try {
            return transactionTemplate.execute(status -> {
                companyRepository.findByIdForUpdate(company.getId()); // COMPANY lock first
                List<Statement> generations = statementRepository
                        .findByCompanyIdOrderByPeriodStartDescVersionDesc(company.getId()).stream()
                        .filter(s -> s.periodStart().equals(period.start()))
                        .toList();
                if (generations.stream().anyMatch(s -> s.status() == StatementStatus.READY)) {
                    return null;
                }
                Statement latest = generations.isEmpty() ? null : generations.get(0);
                if (latest != null && isFreshGenerating(latest)) {
                    return null;
                }
                Statement claim = latest == null
                        ? new Statement(UUID.randomUUID(), company.getId(), period.start(), period.end(),
                                period.periodKey(), StatementStatus.GENERATING, 1, null, null, null,
                                null, null, null, 1, StatementEmailStatus.NONE,
                                null, 0, null, null, null, null, null, null, List.of(), null, null)
                        : statementRepository.findById(latest.id()).orElseThrow().claim();
                return statementRepository.save(claim).id();
            });
        } catch (DataIntegrityViolationException e) {
            // Lost the version race — exactly what the UNIQUE backstop exists for; the
            // winner's fresh GENERATING row is correctly skipped next pass.
            return null;
        }
    }

    private boolean isFreshGenerating(Statement s) {
        return s.status() == StatementStatus.GENERATING && s.updatedAt() != null
                && s.updatedAt().isAfter(Instant.now()
                        .minus(Duration.ofMinutes(properties.getGeneration().getStaleMinutes())));
    }

    @Override
    public boolean process(UUID statementId) {
        Statement claimed = claim(statementId);
        if (claimed == null) {
            return false;
        }
        try {
            generateArtifactsAndFinalize(claimed);
            return true;
        } catch (Exception e) {
            log.warn("Statement generation failed for {}: {}", statementId, e.getMessage());
            handleFailure(statementId);
            return false;
        }
    }

    private Statement claim(UUID statementId) {
        return transactionTemplate.execute(status -> {
            Statement statement = statementRepository.findByIdForUpdate(statementId).orElse(null);
            if (statement == null || statement.status() == StatementStatus.READY) {
                return null;
            }
            return statementRepository.save(statement.claim());
        });
    }

    /** All heavy work — no transaction held anywhere in this method. */
    private void generateArtifactsAndFinalize(Statement claimed) {
        CompanyEntity company = companyJpaRepository.findById(claimed.companyId())
                .orElseThrow(() -> new IllegalStateException("Company vanished: " + claimed.companyId()));

        AccountingTotals totals = accountingRepository.aggregateTotals(
                claimed.companyId(), claimed.periodStart(), claimed.periodEnd());

        List<StatementOrderRow> orderRows = new ArrayList<>();
        UUID after = null;
        while (true) {
            List<StatementOrderRow> page = accountingRepository.findOrderRows(
                    claimed.companyId(), claimed.periodStart(), claimed.periodEnd(), after, ORDER_ROW_PAGE);
            orderRows.addAll(page);
            if (page.size() < ORDER_ROW_PAGE) {
                break;
            }
            after = page.get(page.size() - 1).orderId();
        }

        // Cross-check BEFORE rendering: rendered-row sums must equal the SQL totals.
        BigDecimal gross = orderRows.stream().map(StatementOrderRow::grossTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = orderRows.stream().map(StatementOrderRow::taxTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (orderRows.size() != totals.orderCount()
                || gross.compareTo(totals.grossTotal()) != 0
                || tax.compareTo(totals.taxTotal()) != 0) {
            throw new IllegalStateException("Statement cross-check failed: rows=" + orderRows.size()
                    + "/" + totals.orderCount() + " gross=" + gross + "/" + totals.grossTotal()
                    + " tax=" + tax + "/" + totals.taxTotal());
        }

        List<StatementDiscrepancy> discrepancies = orderRows.stream()
                .filter(row -> row.invoiceStatus() == null || !"READY".equals(row.invoiceStatus()))
                .map(row -> row.invoiceStatus() == null
                        ? new StatementDiscrepancy(StatementDiscrepancyType.INVOICE_MISSING, row.orderId(), null)
                        : new StatementDiscrepancy(StatementDiscrepancyType.INVOICE_NOT_READY, row.orderId(),
                                "invoice status: " + row.invoiceStatus()))
                .toList();

        var companyInfo = new StatementRenderer.CompanyInfo(
                company.getName(), company.getGstNumber(), company.getStatementEmail());
        byte[] pdf = statementRenderer.render(claimed, companyInfo, orderRows);
        byte[] xlsx = workbookWriter.write(claimed, companyInfo, orderRows,
                (afterOrderId, afterLineId) -> accountingRepository.findLinePage(
                        claimed.companyId(), claimed.periodStart(), claimed.periodEnd(),
                        afterOrderId, afterLineId, LINE_PAGE));

        String pdfKey = "statements/" + claimed.companyId() + "/" + claimed.id() + "/statement.pdf";
        String xlsxKey = "statements/" + claimed.companyId() + "/" + claimed.id() + "/statement.xlsx";
        objectStorage.store(pdfKey, pdf, PDF_MEDIA_TYPE);
        objectStorage.store(xlsxKey, xlsx, XLSX_MEDIA_TYPE);

        BigDecimal net = totals.grossTotal().subtract(totals.taxTotal());
        BigDecimal due = totals.grossTotal().subtract(totals.creditTotal());

        transactionTemplate.executeWithoutResult(status -> {
            Statement locked = statementRepository.findByIdForUpdate(claimed.id()).orElse(null);
            if (locked == null || locked.status() == StatementStatus.READY) {
                return; // lost a finalize race — the winner stored this version
            }
            String number = sequenceService.nextNumber(locked.companyId(), locked.periodKey());
            statementRepository.save(locked.markReady(number, pdfKey, xlsxKey, pdf.length, xlsx.length,
                    totals.orderCount(), totals.grossTotal(), totals.taxTotal(), net,
                    totals.creditTotal(), due, discrepancies));
        });
    }

    /** Invoice-scheduler shape: at/over the cap -> DLQ_RETRY, else stay GENERATING for stale reclaim. */
    private void handleFailure(UUID statementId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Statement locked = statementRepository.findByIdForUpdate(statementId).orElse(null);
                if (locked == null || locked.status() == StatementStatus.READY) {
                    return;
                }
                if (locked.attemptCount() >= properties.getGeneration().getMaxAttempts()) {
                    statementRepository.save(locked.markDlqRetry());
                }
                // else: stays GENERATING — the stale cutoff requeues it.
            });
        } catch (Exception e) {
            log.error("Statement failure-handling failed for {}: {}", statementId, e.getMessage());
        }
    }
}
