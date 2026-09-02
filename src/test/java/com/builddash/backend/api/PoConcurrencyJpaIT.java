package com.builddash.backend.api;

import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.PoAttachmentService;
import com.builddash.backend.application.service.PoConversionService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.PoAttachmentExistsException;
import com.builddash.backend.domain.exception.PoUploadInProgressException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.PoTestWorkbooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres races (Testcontainers, no mocks): same-key bulk upload, double
 * conversion, double attachment, concurrent PENDING recovery, and a permission
 * revoke racing a critical mutation. Harness discipline per the corrected 9-B
 * lesson: round-robin fan-out, exactly {@code threads} workers on the barrier.
 */
class PoConcurrencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private PoAttachmentService poAttachmentService;
    @Autowired
    private PoImportService poImportService;
    @Autowired
    private PoConversionService poConversionService;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID companyId;
    private String slug;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        companyId = companyService.create(userId, "PoRaceCo", null, null, null).id();
        addressId = jdbcTemplate.queryForObject(
                "INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code) "
                        + "VALUES (?, ?, 'SITE', 'Plot 1', 'Nagpur', 'MH', '440001') RETURNING id",
                UUID.class, userId, userId);

        Category category = new Category();
        category.setName("po-race-cat");
        category.setSlug("po-race-" + UUID.randomUUID());
        UUID categoryId = categoryRepository.save(category).getId();
        Product product = new Product();
        slug = "po-race-" + UUID.randomUUID();
        product.setName("po-race-product");
        product.setSlug(slug);
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        UUID productId = productRepository.save(product).getId();
        productBasePriceRepository.save(productId, new BigDecimal("10.00"));
    }

    @Test
    void sameIdempotencyKey_concurrentUploads_singleImportAndCart() throws Exception {
        String key = "race-key-" + UUID.randomUUID();

        List<Object> outcomes = runConcurrently(2,
                () -> poImportService.importWorkbook(userId, companyId, key, validFile()));

        long fresh = outcomes.stream().filter(o -> o instanceof PoImportService.ImportResult r && !r.replay()).count();
        long replay = outcomes.stream().filter(o -> o instanceof PoImportService.ImportResult r && r.replay()).count();
        // exactly one fresh parse; the loser replays the winner (either pre-read or DIVE path)
        assertThat(fresh + replay).isEqualTo(2);
        assertThat(fresh).isEqualTo(1);

        UUID importId = jdbcTemplate.queryForObject(
                "SELECT id FROM po_imports WHERE company_id = ? AND idempotency_key = ?",
                UUID.class, companyId, key);
        UUID firstId = ((PoImportService.ImportResult) outcomes.get(0)).poImport().id();
        UUID secondId = ((PoImportService.ImportResult) outcomes.get(1)).poImport().id();
        assertThat(firstId).isEqualTo(importId);
        assertThat(secondId).isEqualTo(importId); // loser received the winner's resource
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE project_id = ? AND cart_type = 'B2B_DRAFT'",
                Integer.class, importId)).isEqualTo(1);
    }

    @Test
    void doubleConversion_singleCart_idempotentCartId() throws Exception {
        UUID importId = poImportService
                .importWorkbook(userId, companyId, "conv-" + UUID.randomUUID(), validFile())
                .poImport().id();

        List<Object> outcomes = runConcurrently(2,
                () -> poConversionService.convert(userId, importId));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes).doesNotContainNull();
        UUID first = (UUID) outcomes.get(0);
        UUID second = (UUID) outcomes.get(1);
        assertThat(first).isEqualTo(second); // same cart, never a duplicate
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_imports WHERE id = ?", String.class, importId))
                .isEqualTo("CONVERTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE project_id = ? AND cart_type = 'B2B_DRAFT'",
                Integer.class, importId)).isEqualTo(1);
    }

    @Test
    void doubleAttachment_oneClaimWins() throws Exception {
        UUID orderId = b2bOrder();

        List<Object> outcomes = runConcurrently(2,
                () -> poAttachmentService.upload(userId, orderId, xlsxFile()));

        long stored = outcomes.stream()
                .filter(o -> o instanceof com.builddash.backend.domain.model.PoAttachment a
                        && a.status() == com.builddash.backend.domain.enums.PoAttachmentStatus.STORED
                        // the upload path always finalizes its own claim; both may succeed
                        // only if serialized — but the unique order_id allows ONE row
                        && true)
                .count();
        long conflicts = outcomes.stream().filter(o ->
                o instanceof PoAttachmentExistsException || o instanceof PoUploadInProgressException).count();
        // either strict serialization (2 stored sequentially is impossible: second sees STORED)
        assertThat(stored).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_attachments WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_attachments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("STORED");
    }

    @Test
    void concurrentPendingRecovery_exactlyOneFinalizes() throws Exception {
        UUID orderId = b2bOrder();
        UUID attachmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO po_attachments (id, order_id, storage_key, content_type, byte_size, "
                        + "uploaded_by, status) VALUES (?, ?, ?, ?, 8, ?, 'PENDING')",
                attachmentId, orderId, "po/" + orderId + "/" + attachmentId + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", userId);

        List<Object> outcomes = runConcurrently(2,
                () -> poAttachmentService.retry(userId, orderId, attachmentId, xlsxFile()));

        long finalizedNow = outcomes.stream()
                .filter(o -> o instanceof PoAttachmentService.RetryOutcome r && r.finalizedNow())
                .count();
        long receivedStored = outcomes.stream()
                .filter(o -> o instanceof PoAttachmentService.RetryOutcome r
                        && r.attachment().status() == com.builddash.backend.domain.enums.PoAttachmentStatus.STORED)
                .count();
        assertThat(finalizedNow).isEqualTo(1);       // conditional finalize: single winner
        assertThat(receivedStored).isEqualTo(2);     // both callers end with the STORED state
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_attachments WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void permissionRevokeRacingBulkMutation_outcomeIsDeterministic() throws Exception {
        UUID memberId = newUser();
        UUID memberRowId = jdbcTemplate.queryForObject(
                "INSERT INTO company_members (id, company_id, user_id, role) "
                        + "VALUES (?, ?, ?, 'PROCUREMENT_MANAGER') RETURNING id",
                UUID.class, UUID.randomUUID(), companyId, memberId);

        List<Object> outcomes = runConcurrently(2,
                () -> poImportService.importWorkbook(memberId, companyId,
                        "perm-" + UUID.randomUUID(), validFile()),
                () -> {
                    jdbcTemplate.update(
                            "DELETE FROM company_role_permissions WHERE company_id = ? "
                                    + "AND role = 'PROCUREMENT_MANAGER' AND permission = 'PO_UPLOAD'",
                            companyId);
                    return "revoked";
                });

        Object importOutcome = outcomes.get(0);
        // Either the import committed before the revoke, or it failed 403 after it —
        // never a half-state: rows and cart exist together or not at all.
        if (importOutcome instanceof PoImportService.ImportResult result) {
            assertThat(result.poImport().validRows()).isGreaterThan(0);
            assertThat(result.poImport().draftCartId()).isNotNull();
        } else {
            assertThat(importOutcome).isInstanceOf(ForbiddenException.class);
        }
        Integer dangling = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_imports WHERE company_id = ? AND status = 'REVIEW' "
                        + "AND draft_cart_id IS NULL AND valid_rows > 0",
                Integer.class, companyId);
        assertThat(dangling).isZero();
        assertThat(memberRowId).isNotNull();
    }

    // ---- helpers (corrected 9-B harness: round-robin fan-out, N workers on an N-party barrier) ----

    private List<Object> runConcurrently(int threads, Callable<?>... tasks) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<?> task = tasks[i % tasks.length];
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private MockMultipartFile validFile() {
        return xlsxOf(new Object[][]{
                {"sku", "quantity"},
                {slug, 10}
        });
    }

    private MockMultipartFile xlsxFile() {
        return xlsxOf(new Object[][]{{"sku", "quantity"}});
    }

    private MockMultipartFile xlsxOf(Object[][] rows) {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                PoTestWorkbooks.workbook(rows));
    }

    private UUID b2bOrder() {
        UUID orderId = UUID.randomUUID();
        UUID validSlotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        jdbcTemplate.update(
                "INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, "
                        + "status, company_id, delivery_slot_lock_id) VALUES (?, ?, ?, ?, ?, 100.00, "
                        + "'PAYMENT_PENDING', ?, ?)",
                orderId, userId, addressId, validSlotId, LocalDate.now(), companyId, UUID.randomUUID());
        return orderId;
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", id);
        return id;
    }
}
