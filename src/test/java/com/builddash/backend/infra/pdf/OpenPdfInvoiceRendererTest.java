package com.builddash.backend.infra.pdf;

import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenPdfInvoiceRendererTest {

    private OpenPdfInvoiceRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new OpenPdfInvoiceRenderer();
    }

    @Test
    void render_validSnapshot_generatesValidPdfWithMagicHeaderAndTaxBreakdown() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String invoiceNumber = "INV-2627-000042";

        OrderInvoiceSnapshot.InvoiceLineItemSnapshot item = new OrderInvoiceSnapshot.InvoiceLineItemSnapshot(
                productId,
                "Ultratech Super Cement 50kg",
                "2523",
                10,
                new BigDecimal("380.00"),
                new BigDecimal("18.00"),
                new BigDecimal("684.00"),
                new BigDecimal("4484.00")
        );

        OrderInvoiceSnapshot snapshot = new OrderInvoiceSnapshot(
                orderId,
                invoiceNumber,
                Instant.parse("2026-08-26T10:00:00Z"),
                "+919876543210",
                "Site 42, Sector 5, Bengaluru, 560001",
                List.of(item),
                new BigDecimal("3800.00"),
                new BigDecimal("684.00"),
                new BigDecimal("4484.00")
        );

        byte[] pdfBytes = renderer.render(snapshot);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(500);

        // Verify PDF magic header (%PDF-)
        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");

        // Verify structural validity using PdfReader
        PdfReader reader = new PdfReader(pdfBytes);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);

        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        String text = extractor.getTextFromPage(1);

        assertThat(text).contains("TAX INVOICE");
        assertThat(text).contains(invoiceNumber);
        assertThat(text).contains(orderId.toString());
        assertThat(text).contains("+919876543210");
        assertThat(text).contains("Ultratech Super Cement 50kg");
        assertThat(text).contains("2523");
        assertThat(text).contains("4484.00");
        assertThat(text).contains("Goods and Services Tax Act");

        reader.close();
    }

    @Test
    void render_nullSnapshot_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.render(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Snapshot must not be null");
    }
}
