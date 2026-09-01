package com.builddash.backend.api;

import com.builddash.backend.application.service.StatementGenerationService;
import com.builddash.backend.application.service.StatementQueryService;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.StatementTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static com.builddash.backend.support.StatementTestFixtures.seedCompany;
import static com.builddash.backend.support.StatementTestFixtures.seedConfirmedOrder;
import static com.builddash.backend.support.StatementTestFixtures.seedCreditNote;
import static com.builddash.backend.support.StatementTestFixtures.seedInvoice;
import static com.builddash.backend.support.StatementTestFixtures.seedMember;
import static com.builddash.backend.support.StatementTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-E end-to-end lifecycle on real Postgres + MinIO: scheduler discovery generates the
 * missing closed month, artifacts store, number allocates, email delivers from STORED
 * artifacts, and the query service returns signed URLs for accounting users.
 */
class StatementLifecycleIT extends AbstractIntegrationTest {

    // August 2026 is a CLOSED month from September 1 onward — the discovery sweep's window.
    private static final Instant AUGUST = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant JULY = Instant.parse("2026-07-15T10:00:00Z");

    @Autowired
    private StatementGenerationService generationService;
    @Autowired
    private com.builddash.backend.application.service.StatementEmailService emailService;
    @Autowired
    private StatementQueryService queryService;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void fullLifecycle_generateStoreNumberEmailQuery() {
        UUID companyId = seedCompany(jdbc, "LifecycleCo", "Asia/Kolkata", "accounts@lifecycle.example");
        UUID owner = seedUser(jdbc);
        seedMember(jdbc, companyId, owner, "OWNER");
        UUID user = seedUser(jdbc);
        UUID order1 = seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        UUID order2 = seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "59.00", "9.00");
        seedInvoice(jdbc, order1, "READY");
        seedInvoice(jdbc, order2, "GENERATING"); // -> INVOICE_NOT_READY discrepancy
        seedCreditNote(jdbc, order1, AUGUST, "18.00");

        int generated = generationService.generateDue();

        assertThat(generated).isGreaterThanOrEqualTo(1);
        var statements = statementRepository.findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId);
        assertThat(statements).hasSize(1);
        Statement statement = statements.get(0);
        assertThat(statement.status()).isEqualTo(StatementStatus.READY);
        assertThat(statement.statementNumber()).matches("ST-\\d{6}-\\d{4}");
        assertThat(statement.periodKey()).isNotNull();
        assertThat(statement.orderCount()).isEqualTo(2);
        assertThat(statement.grossTotal()).isEqualByComparingTo("177.00");
        assertThat(statement.taxTotal()).isEqualByComparingTo("27.00");
        assertThat(statement.netTotal()).isEqualByComparingTo("150.00");
        assertThat(statement.creditTotal()).isEqualByComparingTo("18.00");
        assertThat(statement.dueTotal()).isEqualByComparingTo("159.00");
        assertThat(statement.discrepancies()).hasSize(1);
        assertThat(statement.discrepancies().get(0).orderId()).isEqualTo(order2);
        assertThat(statement.pdfStorageKey()).startsWith("statements/" + companyId + "/");
        assertThat(statement.pdfSizeBytes()).isPositive();
        assertThat(statement.xlsxSizeBytes()).isPositive();

        // Artifacts round-trip through the real object storage via signed URLs.
        var view = queryService.get(owner, statement.id());
        assertThat(view.pdfUrl()).startsWith("http");
        assertThat(view.xlsxUrl()).startsWith("http");
        assertThat(view.pdfUrlExpiresAt()).isAfter(Instant.now());

        // Second sweep is idempotent — no second generation for a READY period.
        assertThat(generationService.generateDue()).isZero();

        // Email: LoggingEmailSender in tests; delivery reads STORED artifacts.
        emailService.sweep();
        Statement afterEmail = statementRepository.findById(statement.id()).orElseThrow();
        assertThat(afterEmail.emailStatus()).isEqualTo(StatementEmailStatus.SENT);
        assertThat(afterEmail.emailedAt()).isNotNull();
    }

    @Test
    void emptyMonth_generatesNothing() {
        UUID companyId = seedCompany(jdbc, "EmptyCo", "Asia/Kolkata", null);
        assertThat(generationService.generateDue()).isZero();
        assertThat(StatementTestFixtures.statementCount(jdbc, companyId)).isZero();
    }

    @Test
    void noStatementEmail_marksSkipped_notFailed() {
        UUID companyId = seedCompany(jdbc, "NoEmailCo", "Asia/Kolkata", null);
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");

        generationService.generateDue();
        Statement statement = statementRepository
                .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId).get(0);
        emailService.deliver(statement.id());

        assertThat(statementRepository.findById(statement.id()).orElseThrow().emailStatus())
                .isEqualTo(StatementEmailStatus.SKIPPED);
    }

    @Test
    void list_latestVersionPerPeriod_newestFirst() {
        UUID companyId = seedCompany(jdbc, "ListCo", "Asia/Kolkata", "a@b.c");
        UUID accountant = seedUser(jdbc);
        seedMember(jdbc, companyId, accountant, "ACCOUNTANT"); // default STATEMENT_VIEW
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        seedConfirmedOrder(jdbc, companyId, user, null, JULY, "59.00", "9.00");

        // Both seeded months (July, August 2026) sit inside the 12-month closed window
        // the discovery sweep examines relative to real now.
        generationService.generateDue();

        var list = queryService.list(accountant, companyId);
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
        assertThat(list.get(0).statement().periodStart())
                .isAfter(list.get(1).statement().periodStart());
    }
}
