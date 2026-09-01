package com.builddash.backend.infra.excel;

import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementAccountingRepository.StatementLine;
import com.builddash.backend.domain.port.StatementRenderer.CompanyInfo;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SxssfStatementWorkbookWriterTest {

    @Test
    void write_threeSheets_allRowsPresent_readBackWithPoi() throws Exception {
        UUID orderId = UUID.randomUUID();
        Statement statement = statement(1, "ST-202609-0001");
        var orderRows = List.of(new StatementOrderRow(orderId, null, Instant.now(),
                new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "READY"));
        List<StatementLine> lines = List.of(new StatementLine(orderId, null, UUID.randomUUID(),
                "Cement Bag", 2, new BigDecimal("50.00"), new BigDecimal("18.00"),
                new BigDecimal("118.00"), UUID.randomUUID()));

        byte[] xlsx = new SxssfStatementWorkbookWriter().write(statement,
                new CompanyInfo("Acme", "27AAAPZ1234C1ZV", "a@b.c"), orderRows,
                (afterOrder, afterLine) -> afterOrder == null ? lines : List.of());

        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(read.getSheet("Summary")).isNotNull();
            assertThat(read.getSheet("Orders")).isNotNull();
            assertThat(read.getSheet("Line Items")).isNotNull();
            assertThat(read.getSheet("Line Items").getLastRowNum()).isEqualTo(lines.size()); // 0-based index: header + N -> N
            assertThat(read.getSheet("Orders").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo(String.valueOf(orderId));
            assertThat(read.getSheet("Line Items").getRow(1).getCell(3).getStringCellValue())
                    .isEqualTo("Cement Bag");
            assertThat(read.getSheet("Line Items").getRow(1).getCell(7).getStringCellValue())
                    .isEqualTo("118.00");
        }
    }

    @Test
    void write_drainsLineSourcePageByPage_keysetAdvances() throws Exception {
        UUID orderId = UUID.randomUUID();
        Statement statement = statement(1, "ST-202609-0002");
        List<StatementLine> page1 = lines(orderId, 0, 3);
        List<StatementLine> page2 = lines(orderId, 3, 3);
        List<UUID[]> cursors = new ArrayList<>();
        var orderRows = List.of(new StatementOrderRow(orderId, null, Instant.now(),
                new BigDecimal("6.00"), new BigDecimal("1.08"), new BigDecimal("7.08"), "READY"));

        byte[] xlsx = new SxssfStatementWorkbookWriter().write(statement,
                new CompanyInfo("Acme", null, null), orderRows,
                new com.builddash.backend.domain.port.StatementWorkbookWriter.StatementLineSource() {
                    private int calls;
                    @Override
                    public List<StatementLine> next(UUID afterOrder, UUID afterLine) {
                        cursors.add(new UUID[]{afterOrder, afterLine});
                        calls++;
                        if (calls == 1) {
                            return page1;
                        }
                        if (calls == 2) {
                            return page2;
                        }
                        return List.of();
                    }
                });

        // Keyset cursor carried the last (order, line) of page1 into page2's fetch.
        assertThat(cursors.size()).isEqualTo(3);
        assertThat(cursors.get(1)[0]).isEqualTo(orderId);
        assertThat(cursors.get(1)[1]).isEqualTo(page1.get(2).lineId());

        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(read.getSheet("Line Items").getLastRowNum()).isEqualTo(6); // header + 6 data rows
        }
    }

    private List<StatementLine> lines(UUID orderId, int from, int count) {
        List<StatementLine> result = new ArrayList<>();
        for (int i = from; i < from + count; i++) {
            result.add(new StatementLine(orderId, null, UUID.randomUUID(), "Item " + i, 1,
                    new BigDecimal("1.00"), new BigDecimal("0.18"), new BigDecimal("1.18"),
                    UUID.randomUUID()));
        }
        return result;
    }

    private Statement statement(int version, String number) {
        return new Statement(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                "202609", StatementStatus.GENERATING, version, number, null, null, null, null, null, 1,
                StatementEmailStatus.NONE, null, 0, 1,
                new BigDecimal("7.08"), new BigDecimal("1.08"), new BigDecimal("6.00"),
                BigDecimal.ZERO, new BigDecimal("7.08"), List.of(), null, null);
    }
}
