package com.builddash.backend.api;

import com.builddash.backend.application.service.StatementGenerationService;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.builddash.backend.support.StatementTestFixtures.seedCompany;
import static com.builddash.backend.support.StatementTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-E large-data proof on real Postgres: 5,000+ order lines across hundreds of orders
 * in one closed month — SQL totals stay exact, the XLSX contains every line (SXSSF
 * streamed, read back with POI), and the number/allocation path stays O(1)-ish.
 */
class StatementLargeScaleIT extends AbstractIntegrationTest {

    private static final Instant AUGUST = Instant.parse("2026-08-15T10:00:00Z");
    private static final int ORDERS = 600;
    private static final int LINES_PER_ORDER = 10; // 6,000 lines

    @Autowired
    private StatementGenerationService generationService;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private com.builddash.backend.domain.port.ObjectStorage objectStorage;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sixThousandLines_generateStreamAndReadBack() throws Exception {
        UUID companyId = seedCompany(jdbc, "BigCo", "Asia/Kolkata", "big@co.example");
        UUID userId = seedUser(jdbc);

        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Bulk', ?)", categoryId, "b" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'BulkItem', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "bulk" + productId, categoryId);

        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'S', 'C', 'MH', '400001', now(), now())", addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111104");

        // Batch-insert ORDERS x LINES_PER_ORDER confirmed lines in one closed month.
        BigDecimal expectedGross = BigDecimal.ZERO;
        BigDecimal expectedTax = BigDecimal.ZERO;
        Timestamp confirmed = Timestamp.from(AUGUST);
        Timestamp created = Timestamp.from(AUGUST.minusSeconds(3600));
        for (int o = 0; o < ORDERS; o++) {
            UUID orderId = UUID.randomUUID();
            jdbc.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, "
                            + "delivery_slot_lock_id, created_at, confirmed_at, company_id) "
                            + "VALUES (?, ?, ?, ?, CURRENT_DATE, 11.80::numeric, 'CONFIRMED', gen_random_uuid(), ?, ?, ?)",
                    orderId, userId, addressId, slotId, created, confirmed, companyId);
            for (int l = 0; l < LINES_PER_ORDER; l++) {
                jdbc.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, "
                                + "tax_amount, line_total) VALUES (?, ?, ?, 1, 1.00::numeric, 0.18::numeric, 1.18::numeric)",
                        UUID.randomUUID(), orderId, productId);
            }
            expectedGross = expectedGross.add(new BigDecimal("11.80"));
            expectedTax = expectedTax.add(new BigDecimal("1.80"));
        }

        int started = generationService.generateDue();
        assertThat(started).isEqualTo(1);

        Statement statement = statementRepository
                .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId).get(0);
        assertThat(statement.status()).isEqualTo(StatementStatus.READY);
        assertThat(statement.orderCount()).isEqualTo(ORDERS);
        assertThat(statement.grossTotal()).isEqualByComparingTo(expectedGross); // 7080.00
        assertThat(statement.taxTotal()).isEqualByComparingTo(expectedTax);     // 1080.00
        assertThat(statement.netTotal()).isEqualByComparingTo(expectedGross.subtract(expectedTax));

        // SXSSF wrote every line; read the workbook back with POI.
        byte[] xlsx = objectStorage.get(statement.xlsxStorageKey());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheet("Orders").getLastRowNum()).isEqualTo(ORDERS);
            assertThat(workbook.getSheet("Line Items").getLastRowNum())
                    .isEqualTo((long) ORDERS * LINES_PER_ORDER);
        }
        assertThat(statement.pdfSizeBytes()).isPositive();
    }
}
