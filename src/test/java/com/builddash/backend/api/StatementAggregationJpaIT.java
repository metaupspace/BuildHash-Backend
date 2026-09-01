package com.builddash.backend.api;

import com.builddash.backend.domain.port.StatementAccountingRepository;
import com.builddash.backend.domain.port.StatementAccountingRepository.AccountingTotals;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.StatementTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.builddash.backend.support.StatementTestFixtures.seedCompany;
import static com.builddash.backend.support.StatementTestFixtures.seedConfirmedOrder;
import static com.builddash.backend.support.StatementTestFixtures.seedCreditNote;
import static com.builddash.backend.support.StatementTestFixtures.seedInvoice;
import static com.builddash.backend.support.StatementTestFixtures.seedSite;
import static com.builddash.backend.support.StatementTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-E accounting aggregation on real Postgres: SQL totals against persisted order-line
 * NUMERIC values, credit notes, invoice-status joins, and the full inclusion matrix.
 */
class StatementAggregationJpaIT extends AbstractIntegrationTest {

    private static final Instant SEPTEMBER = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant AUGUST = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant OCTOBER = Instant.parse("2026-10-05T10:00:00Z");

    @Autowired
    private StatementAccountingRepository accountingRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID company() {
        return seedCompany(jdbc, "AggCo", "Asia/Kolkata", null);
    }

    private AccountingTotals totals(UUID companyId) {
        // September 2026 in Asia/Kolkata.
        return accountingRepository.aggregateTotals(companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"));
    }

    @Test
    void emptyMonth_zeroOrdersZeroCredits() {
        assertThat(totals(company()).orderCount()).isZero();
        assertThat(totals(company()).grossTotal()).isEqualByComparingTo("0.00");
        assertThat(totals(company()).creditNoteCount()).isZero();
    }

    @Test
    void confirmedOrders_aggregateExactNumeralTotals() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "236.00", "36.00");

        AccountingTotals t = totals(companyId);
        assertThat(t.orderCount()).isEqualTo(2);
        assertThat(t.grossTotal()).isEqualByComparingTo("354.00");
        assertThat(t.taxTotal()).isEqualByComparingTo("54.00");
    }

    @Test
    void cancelledOrders_excluded_fromTotalsAndOrderRows() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        UUID kept = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        UUID cancelled = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "100.00", "18.00");
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", cancelled);

        AccountingTotals t = totals(companyId);
        assertThat(t.orderCount()).isEqualTo(1);
        assertThat(t.grossTotal()).isEqualByComparingTo("118.00");

        var rows = accountingRepository.findOrderRows(companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"), null, 100);
        assertThat(rows).extracting(r -> r.orderId()).containsExactly(kept);
    }

    @Test
    void unconfirmedStates_excluded_byMissingConfirmedAt() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        UUID pendingPayment = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        UUID pendingApproval = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        jdbc.update("UPDATE orders SET status = 'PAYMENT_PENDING', confirmed_at = NULL WHERE id = ?", pendingPayment);
        jdbc.update("UPDATE orders SET status = 'PENDING_APPROVAL', confirmed_at = NULL WHERE id = ?", pendingApproval);

        assertThat(totals(companyId).orderCount()).isZero();
    }

    @Test
    void periodBoundaries_halfOpenInCompanyTimezone() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        // Sept 2026 Kolkata: [2026-08-31T18:30Z, 2026-09-30T18:30Z)
        seedConfirmedOrder(jdbc, companyId, user, null, Instant.parse("2026-08-31T18:29:59Z"), "50.00", "9.00");  // August
        seedConfirmedOrder(jdbc, companyId, user, null, Instant.parse("2026-08-31T18:30:00Z"), "118.00", "18.00"); // boundary = Sept
        seedConfirmedOrder(jdbc, companyId, user, null, Instant.parse("2026-09-30T18:29:59Z"), "59.00", "9.00");  // Sept
        seedConfirmedOrder(jdbc, companyId, user, null, Instant.parse("2026-09-30T18:30:00Z"), "25.00", "5.00");   // boundary = Oct

        AccountingTotals t = totals(companyId);
        assertThat(t.orderCount()).isEqualTo(2);
        assertThat(t.grossTotal()).isEqualByComparingTo("177.00");
    }

    @Test
    void credits_countedInNotesPeriod_notOrderPeriod() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        UUID augustOrder = seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        // Credit note for an AUGUST order, generated in SEPTEMBER -> September statement.
        seedCreditNote(jdbc, augustOrder, SEPTEMBER, "18.00");

        AccountingTotals september = totals(companyId);
        assertThat(september.creditNoteCount()).isEqualTo(1);
        assertThat(september.creditTotal()).isEqualByComparingTo("18.00");
        assertThat(september.orderCount()).isZero(); // the order itself belongs to August
    }

    @Test
    void credits_neverLeakAcrossCompanies() {
        UUID a = company();
        UUID b = company();
        UUID user = seedUser(jdbc);
        UUID orderB = seedConfirmedOrder(jdbc, b, user, null, SEPTEMBER, "118.00", "18.00");
        seedCreditNote(jdbc, orderB, SEPTEMBER, "18.00");

        assertThat(totals(a).creditNoteCount()).isZero();
        assertThat(totals(b).creditNoteCount()).isEqualTo(1);
    }

    @Test
    void orderRows_carryInvoiceStatusForDiscrepancies() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        UUID ready = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        UUID generating = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        UUID missing = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        seedInvoice(jdbc, ready, "READY");
        seedInvoice(jdbc, generating, "GENERATING");

        var rows = accountingRepository.findOrderRows(companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"), null, 100);
        assertThat(rows).extracting(r -> r.orderId()).containsExactlyInAnyOrder(ready, generating, missing);
        assertThat(rows).filteredOn(r -> r.orderId().equals(ready)).first()
                .extracting(r -> r.invoiceStatus()).isEqualTo("READY");
        assertThat(rows).filteredOn(r -> r.orderId().equals(generating)).first()
                .extracting(r -> r.invoiceStatus()).isEqualTo("GENERATING");
        assertThat(rows).filteredOn(r -> r.orderId().equals(missing)).first()
                .extracting(r -> r.invoiceStatus()).isNull();
    }

    @Test
    void linePages_keysetOrdered_productNamesJoined() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)", categoryId, "c" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "p" + productId, categoryId);
        UUID orderId = seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "118.00", "18.00");
        jdbc.update("UPDATE order_line_items SET product_id = ? WHERE order_id = ?", productId, orderId);

        var page = accountingRepository.findLinePage(companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                null, null, 100);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).productName()).isEqualTo("Cement Bag");
        assertThat(page.get(0).lineTotal()).isEqualByComparingTo("118.00");
        // Empty continuation past the last key.
        assertThat(accountingRepository.findLinePage(companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                page.get(0).orderId(), page.get(0).lineId(), 100)).isEmpty();
    }

    @Test
    void rounding_paiseExact_noFloatDrift() {
        UUID companyId = company();
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "0.10", "0.02");
        seedConfirmedOrder(jdbc, companyId, user, null, SEPTEMBER, "0.05", "0.01");

        AccountingTotals t = totals(companyId);
        assertThat(t.grossTotal()).isEqualByComparingTo("0.15");
        assertThat(t.taxTotal()).isEqualByComparingTo("0.03");
        assertThat(t.grossTotal().scale()).isEqualTo(2);
    }
}
