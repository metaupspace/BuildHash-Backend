package com.builddash.backend.infra.pdf;

import com.builddash.backend.domain.enums.StatementDiscrepancyType;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementDiscrepancy;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementRenderer.CompanyInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenPdfStatementRendererTest {

    @Test
    void render_producesValidPdfHeaderWithTotalsAndDiscrepancies() throws Exception {
        UUID orderId = UUID.randomUUID();
        Statement statement = new Statement(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                "202609", StatementStatus.GENERATING, 1, null, null, null, null, null, null, 1,
                StatementEmailStatus.NONE, null, 0, 1,
                new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("100.00"),
                new BigDecimal("18.00"), new BigDecimal("100.00"),
                List.of(new StatementDiscrepancy(StatementDiscrepancyType.INVOICE_NOT_READY, orderId,
                        "invoice status: PENDING")),
                null, null);
        var orderRows = List.of(new StatementOrderRow(orderId, null,
                Instant.parse("2026-09-05T10:00:00Z"),
                new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "PENDING"));

        byte[] pdf = new OpenPdfStatementRenderer().render(statement,
                new CompanyInfo("Acme Constructions", "27AAAPZ1234C1ZV", "accounts@acme.example"),
                orderRows);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(500);
        // PDF magic bytes — a real document, not an empty stream.
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
        assertThat(pdf[2]).isEqualTo((byte) 'D');
        assertThat(pdf[3]).isEqualTo((byte) 'F');
    }

    @Test
    void render_largeOrderCount_staysPerOrderNotPerLine() {
        Statement statement = baseStatement();
        List<StatementOrderRow> many = java.util.stream.IntStream.range(0, 2_000)
                .mapToObj(i -> new StatementOrderRow(UUID.randomUUID(), null, Instant.now(),
                        new BigDecimal("1.00"), new BigDecimal("0.18"), new BigDecimal("1.18"), "READY"))
                .toList();

        byte[] pdf = new OpenPdfStatementRenderer().render(statement,
                new CompanyInfo("BigCo", null, null), many);

        assertThat(pdf).isNotEmpty(); // one row per order completes; lines are XLSX's job
    }

    private Statement baseStatement() {
        return new Statement(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                "202609", StatementStatus.GENERATING, 1, null, null, null, null, null, null, 1,
                StatementEmailStatus.NONE, null, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), null, null);
    }
}
