package com.builddash.backend.api;

import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.PoConversionService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.domain.enums.PoImportStatus;
import com.builddash.backend.domain.exception.InvalidPoStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Bulk import lifecycle against real Postgres: row persistence (invalid rows
 * included), draft cart creation, conversion idempotency, FAILED_STRUCTURE
 * consumption of the idempotency key, and generated-id truth (9-B lesson).
 */
class PoImportLifecycleIT extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        userId = newUser();
        companyId = companyService.create(userId, "PoImportCo", null, null, null).id();

        Category category = new Category();
        category.setName("po-cat");
        category.setSlug("po-cat-" + UUID.randomUUID());
        UUID categoryId = categoryRepository.save(category).getId();

        Product product = new Product();
        product.setName("po-product");
        slug = "po-product-" + UUID.randomUUID();
        product.setSlug(slug);
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        UUID productId = productRepository.save(product).getId();
        productBasePriceRepository.save(productId, new BigDecimal("10.00"));
    }

    @Test
    void import_validAndInvalidRows_persistedCartCreated_identitiesMatchDb() {
        byte[] workbook = PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {slug, 100},
                {slug, 50},                  // duplicate slug -> merged
                {"ghost-slug", 1},           // unknown -> INVALID row
                {"bad", "10pcs"}             // wrong type -> INVALID row
        });

        PoImportService.ImportResult result =
                poImportService.importWorkbook(userId, companyId, "key-" + UUID.randomUUID(), file(workbook));

        assertThat(result.replay()).isFalse();
        assertThat(result.poImport().status()).isEqualTo(PoImportStatus.REVIEW);
        assertThat(result.poImport().totalRows()).isEqualTo(4);
        assertThat(result.poImport().validRows()).isEqualTo(2);
        assertThat(result.poImport().invalidRows()).isEqualTo(2);

        // 9-B lesson: returned ids must match persisted rows
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_imports WHERE id = ?", Integer.class,
                result.poImport().id())).isEqualTo(1);
        for (PoImportRow row : result.rows()) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM po_import_rows WHERE id = ?", Integer.class, row.id()))
                    .isEqualTo(1);
        }

        // Invalid rows persisted verbatim with deterministic codes
        List<String> errorCodes = jdbcTemplate.queryForList(
                "SELECT error_code FROM po_import_rows WHERE import_id = ? AND status = 'INVALID' "
                        + "ORDER BY row_index",
                String.class, result.poImport().id());
        assertThat(errorCodes).containsExactly("PRODUCT_SLUG_NOT_FOUND", "CELL_TYPE_INVALID");

        // Draft cart: B2B_DRAFT, company scope, projectId = importId, merged single line
        UUID cartId = result.poImport().draftCartId();
        assertThat(cartId).isNotNull();
        var cart = jdbcTemplate.queryForMap(
                "SELECT cart_type, company_id, project_id FROM carts WHERE id = ?", cartId);
        assertThat(cart.get("cart_type")).isEqualTo("B2B_DRAFT");
        assertThat(cart.get("company_id")).isEqualTo(companyId);
        assertThat(cart.get("project_id")).isEqualTo(result.poImport().id());
        var line = jdbcTemplate.queryForMap(
                "SELECT quantity FROM cart_line_items WHERE cart_id = ?", cartId);
        assertThat(((Number) line.get("quantity")).intValue()).isEqualTo(150);
    }

    @Test
    void convert_marksConverted_idempotent_singleCart() {
        UUID importId = importWorkbookWithOneValidRow();
        UUID cartIdBefore = jdbcTemplate.queryForObject(
                "SELECT draft_cart_id FROM po_imports WHERE id = ?", UUID.class, importId);

        UUID cartId = poConversionService.convert(userId, importId);

        assertThat(cartId).isEqualTo(cartIdBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_imports WHERE id = ?", String.class, importId))
                .isEqualTo("CONVERTED");

        // Idempotent: second conversion returns the same cart, no duplicate
        assertThat(poConversionService.convert(userId, importId)).isEqualTo(cartId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE project_id = ? AND cart_type = 'B2B_DRAFT'",
                Integer.class, importId)).isEqualTo(1);
    }

    @Test
    void convert_zeroValidRows_409_staysReview() {
        byte[] workbook = PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"ghost-slug", 1} // no valid rows
        });
        PoImportService.ImportResult result = poImportService
                .importWorkbook(userId, companyId, "key-" + UUID.randomUUID(), file(workbook));

        assertThat(result.poImport().validRows()).isZero();
        assertThat(result.poImport().draftCartId()).isNull();

        InvalidPoStateException e = catchThrowableOfType(
                () -> poConversionService.convert(userId, result.poImport().id()),
                InvalidPoStateException.class);
        assertThat(e.getCode()).isEqualTo("NO_VALID_ROWS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_imports WHERE id = ?", String.class, result.poImport().id()))
                .isEqualTo("REVIEW");
    }

    @Test
    void structuralFailure_persistsFailedStructure_andConsumesKey() {
        byte[] garbage = "definitely not a zip".getBytes();
        String key = "key-" + UUID.randomUUID();

        PoImportValidationException e = catchThrowableOfType(
                () -> poImportService.importWorkbook(userId, companyId, key, file(garbage)),
                PoImportValidationException.class);
        assertThat(e.getCode()).isEqualTo("INVALID_CONTENT_TYPE");

        UUID failedId = jdbcTemplate.queryForObject(
                "SELECT id FROM po_imports WHERE company_id = ? AND idempotency_key = ?",
                UUID.class, companyId, key);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_imports WHERE id = ?", String.class, failedId))
                .isEqualTo("FAILED_STRUCTURE");

        // Replay with the same key: existing failed resource, no reparse
        PoImportService.ImportResult replay =
                poImportService.importWorkbook(userId, companyId, key, file(validWorkbook()));
        assertThat(replay.replay()).isTrue();
        assertThat(replay.poImport().id()).isEqualTo(failedId);
        assertThat(replay.poImport().status()).isEqualTo(PoImportStatus.FAILED_STRUCTURE);

        // Corrected file under a NEW key succeeds
        PoImportService.ImportResult corrected = poImportService
                .importWorkbook(userId, companyId, "key-" + UUID.randomUUID(), file(validWorkbook()));
        assertThat(corrected.poImport().status()).isEqualTo(PoImportStatus.REVIEW);
    }

    @Test
    void replay_validImport_returnsSameResource() {
        String key = "key-" + UUID.randomUUID();
        PoImportService.ImportResult first =
                poImportService.importWorkbook(userId, companyId, key, file(validWorkbook()));
        PoImportService.ImportResult second =
                poImportService.importWorkbook(userId, companyId, key, file(validWorkbook()));

        assertThat(second.replay()).isTrue();
        assertThat(second.poImport().id()).isEqualTo(first.poImport().id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_imports WHERE company_id = ? AND idempotency_key = ?",
                Integer.class, companyId, key)).isEqualTo(1);
    }

    @Test
    void get_nonMember_404() {
        UUID importId = importWorkbookWithOneValidRow();
        UUID stranger = newUser();

        catchThrowableOfType(() -> poImportService.get(stranger, importId), NotFoundException.class);
        assertThat(poImportService.get(userId, importId).poImport().id()).isEqualTo(importId);
    }

    // ---- helpers ----

    private byte[] validWorkbook() {
        return PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {slug, 10}
        });
    }

    private UUID importWorkbookWithOneValidRow() {
        return poImportService
                .importWorkbook(userId, companyId, "key-" + UUID.randomUUID(), file(validWorkbook()))
                .poImport().id();
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", id);
        return id;
    }
}
