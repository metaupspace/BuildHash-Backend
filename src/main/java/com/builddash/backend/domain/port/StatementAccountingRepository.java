package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.StatementOrderRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only accounting aggregation for statements (9-E). Totals are computed in SQL
 * over the persisted order-line monetary columns; order rows carry the invoice status
 * (joined) so discrepancies resolve without a second round trip. Line pages are
 * keyset-paged for the SXSSF writer — the full accounting dataset is never materialized.
 */
public interface StatementAccountingRepository {

    record AccountingTotals(int orderCount, BigDecimal grossTotal, BigDecimal taxTotal,
                            int creditNoteCount, BigDecimal creditTotal) {
    }

    record StatementLine(UUID orderId, UUID siteId, UUID productId, String productName,
                         int quantity, BigDecimal unitPrice, BigDecimal taxAmount, BigDecimal lineTotal,
                         UUID lineId) {
    }

    /** SQL SUM aggregation over included orders + the period's CREDIT notes (one query each). */
    AccountingTotals aggregateTotals(UUID companyId, Instant periodStart, Instant periodEnd);

    /** Per-order aggregation rows (LEFT JOIN invoices for the discrepancy check), keyset-paged. */
    List<StatementOrderRow> findOrderRows(UUID companyId, Instant periodStart, Instant periodEnd,
                                          UUID afterOrderId, int limit);

    /** Line-item pages for the workbook, ordered by (order_id, line id) keyset. */
    List<StatementLine> findLinePage(UUID companyId, Instant periodStart, Instant periodEnd,
                                     UUID afterOrderId, UUID afterLineId, int limit);
}
