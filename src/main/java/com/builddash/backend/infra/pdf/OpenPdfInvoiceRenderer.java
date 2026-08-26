package com.builddash.backend.infra.pdf;

import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.port.InvoiceRenderer;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Component
public class OpenPdfInvoiceRenderer implements InvoiceRenderer {

    @Override
    public byte[] render(OrderInvoiceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

            // Title
            Paragraph title = new Paragraph("TAX INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Invoice & Order Metadata Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            metaTable.addCell(createCell("Invoice Number: " + (snapshot.invoiceNumber() != null ? snapshot.invoiceNumber() : "N/A"), boldFont, false));
            metaTable.addCell(createCell("Order Date: " + (snapshot.orderPlacedAt() != null ? snapshot.orderPlacedAt().toString() : "N/A"), bodyFont, false));
            metaTable.addCell(createCell("Order ID: " + snapshot.orderId(), bodyFont, false));
            metaTable.addCell(createCell("Customer Phone: " + (snapshot.customerPhone() != null ? snapshot.customerPhone() : "N/A"), bodyFont, false));
            metaTable.addCell(createCell("Delivery Address: " + (snapshot.deliveryAddress() != null ? snapshot.deliveryAddress() : "N/A"), bodyFont, false));
            metaTable.addCell(createCell("", bodyFont, false));

            document.add(metaTable);

            // Line Items Table
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.0f, 1.2f, 0.8f, 1.2f, 1.0f, 1.2f, 1.4f});
            table.setSpacingAfter(15);

            // Header Row
            table.addCell(createHeaderCell("Product", headerFont));
            table.addCell(createHeaderCell("HSN", headerFont));
            table.addCell(createHeaderCell("Qty", headerFont));
            table.addCell(createHeaderCell("Unit Price", headerFont));
            table.addCell(createHeaderCell("GST %", headerFont));
            table.addCell(createHeaderCell("Tax", headerFont));
            table.addCell(createHeaderCell("Total", headerFont));

            if (snapshot.lineItems() != null) {
                for (OrderInvoiceSnapshot.InvoiceLineItemSnapshot item : snapshot.lineItems()) {
                    table.addCell(createCell(item.productName() != null ? item.productName() : item.productId().toString(), bodyFont, true));
                    table.addCell(createCell(item.hsnCode() != null ? item.hsnCode() : "-", bodyFont, true));
                    table.addCell(createCell(String.valueOf(item.quantity()), bodyFont, true));
                    table.addCell(createCell(item.unitPrice() != null ? item.unitPrice().toPlainString() : "0.00", bodyFont, true));
                    table.addCell(createCell(item.taxRate() != null ? item.taxRate().toPlainString() + "%" : "0%", bodyFont, true));
                    table.addCell(createCell(item.taxAmount() != null ? item.taxAmount().toPlainString() : "0.00", bodyFont, true));
                    table.addCell(createCell(item.lineTotal() != null ? item.lineTotal().toPlainString() : "0.00", bodyFont, true));
                }
            }

            document.add(table);

            // Totals Table
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(50);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setSpacingAfter(20);

            totalsTable.addCell(createCell("Subtotal:", boldFont, false));
            totalsTable.addCell(createCell(snapshot.subTotal() != null ? snapshot.subTotal().toPlainString() : "0.00", bodyFont, false));

            totalsTable.addCell(createCell("Total Tax (GST):", boldFont, false));
            totalsTable.addCell(createCell(snapshot.totalTax() != null ? snapshot.totalTax().toPlainString() : "0.00", bodyFont, false));

            totalsTable.addCell(createCell("Grand Total:", boldFont, false));
            totalsTable.addCell(createCell(snapshot.totalAmount() != null ? snapshot.totalAmount().toPlainString() : "0.00", boldFont, false));

            document.add(totalsTable);

            // Footer Note
            Paragraph footer = new Paragraph("This is a computer-generated tax invoice issued under the Goods and Services Tax Act.", bodyFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF invoice", e);
        }
    }

    private PdfPCell createHeaderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell createCell(String text, Font font, boolean border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        if (!border) {
            cell.setBorder(PdfPCell.NO_BORDER);
        }
        cell.setPadding(4);
        return cell;
    }
}
