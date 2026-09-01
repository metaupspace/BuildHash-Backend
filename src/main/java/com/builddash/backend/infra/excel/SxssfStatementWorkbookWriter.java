package com.builddash.backend.infra.excel;

import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementRenderer.CompanyInfo;
import com.builddash.backend.domain.port.StatementAccountingRepository.StatementLine;
import com.builddash.backend.domain.port.StatementWorkbookWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Statement XLSX (9-E): SXSSF streaming with a small sliding window — the full line
 * dataset is drained page by page through StatementLineSource, never materialized.
 * Sheets: Summary, Orders, Line Items. Money cells are written as plain strings of
 * BigDecimal.toPlainString() to keep NUMERIC(12,2) semantics verbatim (no float
 * round-trip through Excel numeric cells for accounting values).
 */
@Component
public class SxssfStatementWorkbookWriter implements StatementWorkbookWriter {

    @Override
    public byte[] write(Statement statement, CompanyInfo company, List<StatementOrderRow> orderRows,
                        StatementLineSource lineSource) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            workbook.setCompressTempFiles(true);
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);

            writeSummary(workbook, header, statement, company);
            writeOrders(workbook, header, orderRows);
            writeLines(workbook, header, lineSource);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render statement XLSX", e);
        }
    }

    private void writeSummary(SXSSFWorkbook workbook, CellStyle header, Statement s, CompanyInfo company) {
        SXSSFSheet sheet = workbook.createSheet("Summary");
        sheet.createFreezePane(0, 1);
        row(sheet, header, "Field", "Value");
        row(sheet, null, "Company", company.name());
        row(sheet, null, "GSTIN", company.gstNumber() == null ? "" : company.gstNumber());
        row(sheet, null, "Statement email", company.statementEmail() == null ? "" : company.statementEmail());
        row(sheet, null, "Statement number", s.statementNumber() == null ? "" : s.statementNumber());
        row(sheet, null, "Version", String.valueOf(s.version()));
        row(sheet, null, "Period", s.periodKey());
        row(sheet, null, "Period start (UTC)", String.valueOf(s.periodStart()));
        row(sheet, null, "Period end (UTC)", String.valueOf(s.periodEnd()));
        row(sheet, null, "Order count", String.valueOf(s.orderCount() == null ? 0 : s.orderCount()));
        row(sheet, null, "Net total (ex GST)", plain(s.netTotal()));
        row(sheet, null, "Total tax (GST)", plain(s.taxTotal()));
        row(sheet, null, "Gross total", plain(s.grossTotal()));
        row(sheet, null, "Credits", plain(s.creditTotal()));
        row(sheet, null, "Amount due", plain(s.dueTotal()));
        int d = 1;
        for (var discrepancy : s.discrepancies()) {
            row(sheet, null, "Discrepancy " + d++, discrepancy.type() + " order " + discrepancy.orderId()
                    + (discrepancy.detail() == null ? "" : " (" + discrepancy.detail() + ")"));
        }
    }

    private void writeOrders(SXSSFWorkbook workbook, CellStyle header, List<StatementOrderRow> orderRows) {
        SXSSFSheet sheet = workbook.createSheet("Orders");
        sheet.createFreezePane(0, 1);
        row(sheet, header, "OrderId", "SiteId", "ConfirmedAt", "Net", "Tax", "Gross", "InvoiceStatus");
        for (StatementOrderRow r : orderRows) {
            row(sheet, null, String.valueOf(r.orderId()),
                    r.siteId() == null ? "" : String.valueOf(r.siteId()),
                    r.confirmedAt() == null ? "" : r.confirmedAt().toString(),
                    plain(r.netTotal()), plain(r.taxTotal()), plain(r.grossTotal()),
                    r.invoiceStatus() == null ? "MISSING" : r.invoiceStatus());
        }
    }

    private void writeLines(SXSSFWorkbook workbook, CellStyle header, StatementLineSource lineSource) {
        SXSSFSheet sheet = workbook.createSheet("Line Items");
        sheet.createFreezePane(0, 1);
        row(sheet, header, "OrderId", "SiteId", "ProductId", "ProductName", "Quantity",
                "UnitPrice", "TaxAmount", "LineTotal");
        UUID afterOrder = null;
        UUID afterLine = null;
        while (true) {
            List<StatementLine> page = lineSource.next(afterOrder, afterLine);
            if (page.isEmpty()) {
                break;
            }
            for (StatementLine l : page) {
                row(sheet, null, String.valueOf(l.orderId()),
                        l.siteId() == null ? "" : String.valueOf(l.siteId()),
                        String.valueOf(l.productId()),
                        l.productName() == null ? "" : l.productName(),
                        String.valueOf(l.quantity()),
                        plain(l.unitPrice()), plain(l.taxAmount()), plain(l.lineTotal()));
            }
            StatementLine last = page.get(page.size() - 1);
            afterOrder = last.orderId();
            afterLine = last.lineId();
        }
    }

    private void row(SXSSFSheet sheet, CellStyle header, String... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            if (header != null) {
                cell.setCellStyle(header);
            }
        }
    }

    private String plain(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }
}
