package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementAccountingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Statement accounting reads (9-E). Totals aggregate in SQL over the persisted
 * order-line monetary columns (LEFT JOIN so zero-line orders still count); credits
 * join gst_notes -> returns -> orders. Order rows join the invoice status in the same
 * pass (discrepancy check without a second round trip). Line pages are keyset-paged
 * on (order_id, id) with a fetchSize hint — the full dataset is never materialized.
 */
@Repository
@RequiredArgsConstructor
class StatementAccountingRepositoryAdapter implements StatementAccountingRepository {

    /** ponytail: fixed page size — make configurable only if a statement render ever needs it. */
    private static final int PAGE_SIZE = 1000;

    private final JdbcTemplate jdbc;

    @Override
    public AccountingTotals aggregateTotals(UUID companyId, Instant periodStart, Instant periodEnd) {
        TotalsRow t = jdbc.queryForObject("""
                        SELECT count(DISTINCT o.id) AS order_count,
                               COALESCE(SUM(li.line_total), 0) AS gross,
                               COALESCE(SUM(li.tax_amount), 0) AS tax
                        FROM orders o
                        LEFT JOIN order_line_items li ON li.order_id = o.id
                        WHERE o.company_id = ? AND o.confirmed_at >= ? AND o.confirmed_at < ?
                          AND o.status <> 'CANCELLED'
                        """,
                (rs, i) -> new TotalsRow(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
                companyId, Timestamp.from(periodStart), Timestamp.from(periodEnd));

        CreditRow c = jdbc.queryForObject("""
                        SELECT count(*) AS note_count, COALESCE(SUM(n.amount), 0) AS credit
                        FROM gst_notes n
                        JOIN returns r ON r.id = n.return_id
                        JOIN orders o ON o.id = r.order_id
                        WHERE n.note_type = 'CREDIT' AND n.generated_at >= ? AND n.generated_at < ?
                          AND o.company_id = ?
                        """,
                (rs, i) -> new CreditRow(rs.getInt(1), rs.getBigDecimal(2)),
                Timestamp.from(periodStart), Timestamp.from(periodEnd), companyId);

        return new AccountingTotals(t.orderCount(), scale(t.gross()), scale(t.tax()),
                c.noteCount(), scale(c.credit()));
    }

    @Override
    public List<StatementOrderRow> findOrderRows(UUID companyId, Instant periodStart, Instant periodEnd,
                                                 UUID afterOrderId, int limit) {
        return jdbc.query("""
                        SELECT o.id, o.site_id, o.confirmed_at,
                               COALESCE(SUM(li.line_total), 0) AS gross,
                               COALESCE(SUM(li.tax_amount), 0) AS tax,
                               i.status AS invoice_status
                        FROM orders o
                        LEFT JOIN order_line_items li ON li.order_id = o.id
                        LEFT JOIN invoices i ON i.order_id = o.id
                        WHERE o.company_id = ? AND o.confirmed_at >= ? AND o.confirmed_at < ?
                          AND o.status <> 'CANCELLED'
                          AND (?::uuid IS NULL OR o.id > ?::uuid)
                        GROUP BY o.id, o.site_id, o.confirmed_at, i.status
                        ORDER BY o.id
                        LIMIT ?
                        """,
                (rs, i) -> {
                    BigDecimal gross = rs.getBigDecimal(4);
                    BigDecimal tax = rs.getBigDecimal(5);
                    return new StatementOrderRow(rs.getObject(1, UUID.class),
                            rs.getObject(2, UUID.class),
                            rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant(),
                            scale(gross.subtract(tax)), scale(tax), scale(gross),
                            rs.getString(6));
                },
                companyId, Timestamp.from(periodStart), Timestamp.from(periodEnd),
                afterOrderId, afterOrderId, limit);
    }

    @Override
    public List<StatementLine> findLinePage(UUID companyId, Instant periodStart, Instant periodEnd,
                                            UUID afterOrderId, UUID afterLineId, int limit) {
        return jdbc.query("""
                        SELECT li.order_id, o.site_id, li.product_id, p.name, li.quantity,
                               li.unit_price, li.tax_amount, li.line_total, li.id
                        FROM order_line_items li
                        JOIN orders o ON o.id = li.order_id
                        LEFT JOIN products p ON p.id = li.product_id
                        WHERE o.company_id = ? AND o.confirmed_at >= ? AND o.confirmed_at < ?
                          AND o.status <> 'CANCELLED'
                          AND ((?::uuid IS NULL AND ?::uuid IS NULL) OR (li.order_id, li.id) > (?::uuid, ?::uuid))
                        ORDER BY li.order_id, li.id
                        LIMIT ?
                        """,
                (rs, i) -> new StatementLine(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getInt(5), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getObject(9, UUID.class)),
                companyId, Timestamp.from(periodStart), Timestamp.from(periodEnd),
                afterOrderId, afterLineId, afterOrderId, afterLineId, limit);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private record TotalsRow(int orderCount, BigDecimal gross, BigDecimal tax) {
    }

    private record CreditRow(int noteCount, BigDecimal credit) {
    }
}
