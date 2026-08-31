package com.builddash.backend.api;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.PoAttachmentService;
import com.builddash.backend.domain.enums.PoAttachmentStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.PoAttachmentExistsException;
import com.builddash.backend.domain.exception.PoUploadInProgressException;
import com.builddash.backend.domain.model.PoAttachment;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.PoTestWorkbooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * PO attachment lifecycle against real Postgres + MinIO (the actual S3
 * adapter): fresh claim, B2C/B2C-missing rejections, STORED conflict, PENDING
 * conflict without overwrite, and explicit PENDING recovery through the same
 * storage key.
 */
class PoAttachmentLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private PoAttachmentService poAttachmentService;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private AddressService addressService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID companyId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        companyId = companyService.create(userId, "PoAttachCo", null, null, null).id();
        addressId = addressService.createAddress(userId, "SITE", "Plot 1", null, "Nagpur", "MH", "440001").id();
    }

    @Test
    void upload_happyPath_claimsStoresPersistsStore() {
        UUID orderId = b2bOrder(null);

        PoAttachment stored = poAttachmentService.upload(userId, orderId, xlsx());

        assertThat(stored.status()).isEqualTo(PoAttachmentStatus.STORED);
        // 9-B lesson: returned identity must match the persisted row
        var row = jdbcTemplate.queryForMap(
                "SELECT status, storage_key, byte_size, content_type FROM po_attachments WHERE id = ?",
                stored.id());
        assertThat(row.get("status")).isEqualTo("STORED");
        assertThat(row.get("storage_key")).isEqualTo(stored.storageKey());
        assertThat(((Number) row.get("byte_size")).intValue()).isEqualTo(stored.byteSize());
        assertThat(row.get("content_type")).isEqualTo(stored.contentType());
    }

    @Test
    void upload_orderMissing_404() {
        catchThrowableOfType(() -> poAttachmentService.upload(userId, UUID.randomUUID(), xlsx()),
                NotFoundException.class);
    }

    @Test
    void upload_b2cOrder_404_hidden() {
        UUID b2c = insertOrder(userId, null, null);

        NotFoundException e = catchThrowableOfType(
                () -> poAttachmentService.upload(userId, b2c, xlsx()), NotFoundException.class);
        assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_attachments WHERE order_id = ?", Integer.class, b2c)).isZero();
    }

    @Test
    void upload_secondUploadAfterStored_409() {
        UUID orderId = b2bOrder(null);
        poAttachmentService.upload(userId, orderId, xlsx());

        PoAttachmentExistsException e = catchThrowableOfType(
                () -> poAttachmentService.upload(userId, orderId, xlsx()),
                PoAttachmentExistsException.class);
        assertThat(e.getCode()).isEqualTo("PO_ALREADY_EXISTS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_attachments WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void upload_pendingClaim_409_namesClaim_neverOverwrites() {
        UUID orderId = b2bOrder(null);
        UUID attachmentId = insertPendingClaim(orderId);

        PoUploadInProgressException e = catchThrowableOfType(
                () -> poAttachmentService.upload(userId, orderId, xlsx()),
                PoUploadInProgressException.class);
        assertThat(e.getCode()).isEqualTo("PO_UPLOAD_IN_PROGRESS");
        assertThat(e.getMessage()).contains(attachmentId.toString());
        // claim untouched: still PENDING with the original key
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_attachments WHERE id = ?", String.class, attachmentId))
                .isEqualTo("PENDING");
    }

    @Test
    void retry_pendingClaim_completesThroughSameKey() {
        UUID orderId = b2bOrder(null);
        UUID attachmentId = insertPendingClaim(orderId);
        String originalKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM po_attachments WHERE id = ?", String.class, attachmentId);

        PoAttachmentService.RetryOutcome outcome =
                poAttachmentService.retry(userId, orderId, attachmentId, xlsx());

        assertThat(outcome.finalizedNow()).isTrue();
        assertThat(outcome.attachment().status()).isEqualTo(PoAttachmentStatus.STORED);
        // same claim row, same key — no second attachment, no new object
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM po_attachments WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT storage_key FROM po_attachments WHERE id = ?", String.class, attachmentId))
                .isEqualTo(originalKey);
    }

    @Test
    void retry_afterStoreFailedBeforeFinalize_converges() {
        // Simulate "store succeeded, Tx2 never ran": claim PENDING, object absent.
        UUID orderId = b2bOrder(null);
        UUID attachmentId = insertPendingClaim(orderId);

        PoAttachmentService.RetryOutcome outcome =
                poAttachmentService.retry(userId, orderId, attachmentId, xlsx());

        assertThat(outcome.finalizedNow()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM po_attachments WHERE id = ?", String.class, attachmentId))
                .isEqualTo("STORED");
    }

    @Test
    void siteScope_outOfScopeMember_403() {
        UUID siteId = createSite();
        // member scoped to a different site of the same company
        UUID otherSite = createSite();
        UUID memberId = newUser();
        jdbcTemplate.update(
                "INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'SITE_SUPERVISOR')",
                UUID.randomUUID(), companyId, memberId);
        jdbcTemplate.update(
                "INSERT INTO company_site_assignments (member_id, site_id) VALUES "
                        + "((SELECT id FROM company_members WHERE user_id = ? AND company_id = ?), ?)",
                memberId, companyId, otherSite);

        UUID orderId = b2bOrder(siteId);

        var e = catchThrowableOfType(
                () -> poAttachmentService.upload(memberId, orderId, xlsx()),
                com.builddash.backend.domain.exception.ForbiddenException.class);
        assertThat(e.getCode()).isIn("FORBIDDEN", "SITE_OUT_OF_SCOPE");
    }

    // ---- helpers ----

    private MockMultipartFile xlsx() {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                PoTestWorkbooks.workbook(new Object[][]{{"sku", "quantity"}}));
    }

    private UUID b2bOrder(UUID siteId) {
        return insertOrder(userId, companyId, siteId);
    }

    private UUID insertOrder(UUID owner, UUID company, UUID site) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, "
                        + "status, company_id, site_id, delivery_slot_lock_id) VALUES (?, ?, ?, ?, ?, 100.00, "
                        + "'PAYMENT_PENDING', ?, ?, ?)",
                orderId, owner, addressId, UUID.randomUUID(), LocalDate.now(), company, site, UUID.randomUUID());
        return orderId;
    }

    private UUID createSite() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO company_sites (id, company_id, name) VALUES (?, ?, ?) RETURNING id",
                UUID.class, UUID.randomUUID(), companyId, "Site-" + UUID.randomUUID());
    }

    private UUID insertPendingClaim(UUID orderId) {
        UUID attachmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO po_attachments (id, order_id, storage_key, content_type, byte_size, "
                        + "uploaded_by, status) VALUES (?, ?, ?, ?, 8, ?, 'PENDING')",
                attachmentId, orderId, "po/" + orderId + "/" + attachmentId + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", userId);
        return attachmentId;
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", id);
        return id;
    }
}
