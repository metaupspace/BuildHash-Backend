package com.builddash.backend.infra.pdf;

import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementRenderer;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/**
 * Statement PDF (9-E) — invoice-renderer conventions: programmatic OpenPDF, A4, 36pt
 * margins, built-in Helvetica, BigDecimal.toPlainString(). Large-statement strategy:
 * one row per ORDER (never per line item); full line detail belongs to the XLSX.
 */
@Component
public class OpenPdfStatementRenderer implements StatementRenderer {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

    @Override
    public byte[] render(Statement statement, CompanyInfo company, java.util.List<StatementOrderRow> orderRows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Phrase("MONTHLY STATEMENT", TITLE));
            document.add(phrase("Company: " + company.name(), BODY));
            if (company.gstNumber() != null) {
                document.add(phrase("GSTIN: " + company.gstNumber(), BODY));
            }
            if (company.statementEmail() != null) {
                document.add(phrase("Statement email: " + company.statementEmail(), BODY));
            }
            document.add(phrase("Statement number: " + statement.statementNumber()
                    + "  (version " + statement.version() + ")", BODY));
            document.add(phrase("Period: " + statement.periodKey()
                    + "  [" + statement.periodStart() + " – " + statement.periodEnd() + ")", BODY));
            document.add(phrase("", BODY));

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(40);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsRow(totals, "Orders", String.valueOf(statement.orderCount() == null ? 0 : statement.orderCount()));
            totalsRow(totals, "Net total (ex GST)", plain(statement.netTotal()));
            totalsRow(totals, "Total tax (GST)", plain(statement.taxTotal()));
            totalsRow(totals, "Gross total", plain(statement.grossTotal()));
            totalsRow(totals, "Credits", "-" + plain(statement.creditTotal()));
            totalsRow(totals, "Amount due", plain(statement.dueTotal()));
            document.add(totals);
            document.add(phrase("", BODY));

            PdfPTable orders = new PdfPTable(6);
            orders.setWidthPercentage(100);
            orders.setWidths(new float[]{2.6f, 2.0f, 1.8f, 1.4f, 1.4f, 1.6f});
            cell(orders, "Order", HEADER);
            cell(orders, "Site", HEADER);
            cell(orders, "Confirmed", HEADER);
            cell(orders, "Net", HEADER);
            cell(orders, "Tax", HEADER);
            cell(orders, "Gross", HEADER);
            for (StatementOrderRow row : orderRows) {
                cell(orders, String.valueOf(row.orderId()), SMALL);
                cell(orders, row.siteId() == null ? "-" : String.valueOf(row.siteId()), SMALL);
                cell(orders, row.confirmedAt() == null ? "-" : row.confirmedAt().toString(), SMALL);
                cell(orders, plain(row.netTotal()), SMALL);
                cell(orders, plain(row.taxTotal()), SMALL);
                cell(orders, plain(row.grossTotal()), SMALL);
            }
            document.add(orders);

            if (!statement.discrepancies().isEmpty()) {
                document.add(phrase("", BODY));
                document.add(phrase("Discrepancies (" + statement.discrepancies().size() + "):", HEADER));
                statement.discrepancies().forEach(d -> document.add(
                        phrase("  " + d.type() + " — order " + d.orderId()
                                + (d.detail() == null ? "" : " (" + d.detail() + ")"), SMALL)));
            }

            document.add(phrase("", BODY));
            document.add(phrase("Computer-generated monthly statement. Totals aggregate confirmed order "
                    + "line values; credits are GST credit notes issued in the period.", SMALL));
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render statement PDF", e);
        }
    }

    private Phrase phrase(String text, Font font) {
        return new Phrase(text + "\n", font);
    }

    private void cell(PdfPTable table, String text, Font font) {
        var cell = new com.lowagie.text.pdf.PdfPCell(new Phrase(text, font));
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private void totalsRow(PdfPTable table, String label, String value) {
        var labelCell = new com.lowagie.text.pdf.PdfPCell(new Phrase(label, BODY));
        labelCell.setPadding(4f);
        table.addCell(labelCell);
        var valueCell = new com.lowagie.text.pdf.PdfPCell(new Phrase(value, BODY));
        valueCell.setPadding(4f);
        table.addCell(valueCell);
    }

    private String plain(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.toPlainString() : value.toPlainString();
    }
}
