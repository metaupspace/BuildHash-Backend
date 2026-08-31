package com.builddash.backend.api;

import com.builddash.backend.application.scheduler.RfqExpirySweeper;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.application.service.VendorAdminService;
import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.domain.exception.DuplicateQuoteException;
import com.builddash.backend.domain.exception.InvalidRfqStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.QuoteValidationException;
import com.builddash.backend.domain.exception.RfqValidationException;
import com.builddash.backend.domain.exception.VendorNotRoutableException;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.RfqQuoteRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 9-B lifecycle against real Postgres: creation validation, creation-time
 * routing (any-category match, multi-category, none), controlled quote
 * submission rules, expiry sweep, cancellation, and conversion into a
 * B2B_DRAFT cart.
 */
class RfqLifecycleJpaIT extends AbstractIntegrationTest {

    @Autowired
    private RfqService rfqService;
    @Autowired
    private VendorAdminService vendorAdminService;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private RfqExpirySweeper rfqExpirySweeper;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private RfqQuoteRepository rfqQuoteRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerUserId;
    private UUID companyId;
    private UUID categoryId;
    private UUID otherCategoryId;
    private UUID productId;
    private UUID otherProductId;
    private Instant future;

    @BeforeEach
    void setUp() {
        ownerUserId = newUser();
        companyId = companyService.create(ownerUserId, "RfqCo", null, null, null).id();
        categoryId = newCategory("cement");
        otherCategoryId = newCategory("steel");
        productId = newProduct("cement-bag", categoryId);
        otherProductId = newProduct("steel-rod", otherCategoryId);
        future = Instant.now().plusSeconds(3600);
    }

    // ---- creation validation ----

    @Test
    void create_emptyItems_rejected() {
        assertThatThrownBy(() -> rfqService.create(ownerUserId, companyId, future, null, List.of()))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code").isEqualTo("RFQ_ITEMS_REQUIRED");
    }

    @Test
    void create_missingProduct_throwsNotFound() {
        assertThatThrownBy(() -> rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(UUID.randomUUID(), 5))))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void create_invalidQuantity_rejected() {
        assertThatThrownBy(() -> rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 0))))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code").isEqualTo("RFQ_QUANTITY_INVALID");
    }

    @Test
    void create_invalidExpiry_rejected() {
        assertThatThrownBy(() -> rfqService.create(ownerUserId, companyId,
                Instant.now().minusSeconds(60), null,
                List.of(new RfqService.ItemCommand(productId, 5))))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code").isEqualTo("RFQ_EXPIRY_INVALID");
    }

    @Test
    void create_atomicRollback_leavesNoRowsWhenItFails() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 5),
                        new RfqService.ItemCommand(missing, 5))))
                .isInstanceOf(NotFoundException.class);

        // Second item failed validation: no RFQ, no items, no routes committed
        // (counts scoped to this company — other tests' rows are not ours to judge).
        Integer rfqs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfqs WHERE company_id = ?", Integer.class, companyId);
        Integer items = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_items i JOIN rfqs r ON r.id = i.rfq_id "
                        + "WHERE r.company_id = ?", Integer.class, companyId);
        Integer routes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_routes rt JOIN rfqs r ON r.id = rt.rfq_id "
                        + "WHERE r.company_id = ?", Integer.class, companyId);
        assertThat(rfqs).isZero();
        assertThat(items).isZero();
        assertThat(routes).isZero();
    }

    // ---- routing ----

    @Test
    void routing_anyCategoryMatch_routesVendor() {
        Vendor vendor = vendorAdminService.create("AnyCement",
                List.of(categoryId, otherCategoryId));
        Vendor unrelated = vendorAdminService.create("PlumbingOnly", List.of(newCategory("plumbing")));

        Rfq rfq = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 100)));

        assertThat(rfq.routedVendorIds()).containsExactly(vendor.id());
        assertThat(rfq.routedVendorIds()).doesNotContain(unrelated.id());
        assertThat(rfq.status()).isEqualTo(RfqStatus.OPEN);

        Integer routeRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_routes WHERE rfq_id = ?", Integer.class, rfq.id());
        assertThat(routeRows).isEqualTo(1);
    }

    @Test
    void routing_multiCategoryRfq_distinctMatchesOnly() {
        Vendor both = vendorAdminService.create("Both", List.of(categoryId, otherCategoryId));
        Vendor cementOnly = vendorAdminService.create("CementOnly", List.of(categoryId));

        Rfq rfq = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 100),
                        new RfqService.ItemCommand(otherProductId, 50)));

        // A vendor matching both of the RFQ's categories still yields ONE route row.
        assertThat(rfq.routedVendorIds()).containsExactlyInAnyOrder(both.id(), cementOnly.id());
        Integer distinctRows = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT vendor_id) FROM rfq_routes WHERE rfq_id = ?",
                Integer.class, rfq.id());
        assertThat(distinctRows).isEqualTo(2);
    }

    @Test
    void routing_noMatchingVendors_staysOpenWithEmptyRoutes() {
        vendorAdminService.create("PlumbingOnly", List.of(newCategory("plumbing")));

        Rfq rfq = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 100)));

        assertThat(rfq.status()).isEqualTo(RfqStatus.OPEN);
        assertThat(rfq.routedVendorIds()).isEmpty();
    }

    @Test
    void routing_neverRecalculatedAfterVendorCategoryChange() {
        Vendor vendor = vendorAdminService.create("CementOnly", List.of(categoryId));
        Rfq rfq = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 100)));
        assertThat(rfq.routedVendorIds()).containsExactly(vendor.id());

        // Vendor later drops the category entirely: historical route remains.
        vendorAdminService.update(vendor.id(), null, List.of(otherCategoryId), null);

        List<UUID> routes = jdbcTemplate.queryForList(
                "SELECT vendor_id FROM rfq_routes WHERE rfq_id = ?", UUID.class, rfq.id());
        assertThat(routes).containsExactly(vendor.id());
    }

    // ---- quote submission rules ----

    @Test
    void submitQuote_inactiveVendor_rejected422() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);
        vendorAdminService.update(vendor.id(), null, null, false);

        assertThatThrownBy(() -> quote(rfq, vendor))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code").isEqualTo("VENDOR_INACTIVE");
    }

    @Test
    void submitQuote_unroutedVendor_rejected422() {
        Vendor routed = vendorAdminService.create("Routed", List.of(categoryId));
        Vendor bystander = vendorAdminService.create("Bystander", List.of(otherCategoryId));
        Rfq rfq = openRfqWithVendor(routed);

        assertThatThrownBy(() -> quote(rfq, bystander))
                .isInstanceOf(VendorNotRoutableException.class)
                .extracting("code").isEqualTo("VENDOR_NOT_ROUTED");
    }

    @Test
    void submitQuote_duplicate_sequentialRejected() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);
        quote(rfq, vendor);

        assertThatThrownBy(() -> quote(rfq, vendor))
                .isInstanceOf(DuplicateQuoteException.class)
                .extracting("code").isEqualTo("DUPLICATE_QUOTE");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_quotes WHERE rfq_id = ?", Integer.class, rfq.id());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void submitQuote_invalidValidity_rejected422() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendor.id(),
                BigDecimal.TEN, Instant.now().minusSeconds(1)))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code").isEqualTo("QUOTE_VALIDITY_INVALID");
    }

    // ---- comparison ----

    @Test
    void listQuotes_orderedAscending_expiredFlagComputedAndRetained() {
        // All three share the RFQ item's category, so creation-time routing covers them.
        Vendor cheap = vendorAdminService.create("Cheap", List.of(categoryId));
        Vendor pricey = vendorAdminService.create("Pricey", List.of(categoryId));
        Vendor gone = vendorAdminService.create("Gone", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(cheap);

        rfqService.submitQuote(rfq.id(), pricey.id(), new BigDecimal("500.00"),
                Instant.now().plusSeconds(3600));
        rfqService.submitQuote(rfq.id(), cheap.id(), new BigDecimal("120.50"),
                Instant.now().plusSeconds(3600));
        // Submit the third validly, then backdate its validity horizon: expired
        // quotes are listed historically with expired=true (deterministic, no
        // sub-millisecond timing games).
        RfqQuote goneQuote = rfqService.submitQuote(rfq.id(), gone.id(),
                new BigDecimal("90.00"), Instant.now().plusSeconds(3600));
        jdbcTemplate.update("UPDATE rfq_quotes SET valid_until = now() - interval '1 hour' WHERE id = ?",
                goneQuote.id());

        List<RfqService.QuoteComparison> comparisons =
                rfqService.listQuotes(ownerUserId, rfq.id());

        assertThat(comparisons).hasSize(3);
        assertThat(comparisons.stream().map(c -> c.quote().totalAmount()))
                .containsExactly(new BigDecimal("90.00"), new BigDecimal("120.50"), new BigDecimal("500.00"));
        assertThat(comparisons.get(0).expired()).isTrue();
        assertThat(comparisons.get(0).vendor().name()).isEqualTo("Gone");
        assertThat(comparisons.get(1).expired()).isFalse();
    }

    // ---- expiry ----

    @Test
    void expirySweeper_transitionsOnlyDueOpenRfqs() {
        Rfq due = openRfqExpiringAt(Instant.now().minusSeconds(60));
        Rfq notDue = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 1)));
        Rfq cancelledDue = openRfqExpiringAt(Instant.now().minusSeconds(60));
        rfqService.cancel(ownerUserId, cancelledDue.id());

        int expired = rfqExpirySweeper.sweep();

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(rfqService.get(ownerUserId, due.id()).status()).isEqualTo(RfqStatus.EXPIRED);
        assertThat(rfqService.get(ownerUserId, notDue.id()).status()).isEqualTo(RfqStatus.OPEN);
        assertThat(rfqService.get(ownerUserId, cancelledDue.id()).status()).isEqualTo(RfqStatus.CANCELLED);

        // Expiry is terminal: no submission, no conversion afterwards.
        assertThatThrownBy(() -> rfqService.cancel(ownerUserId, due.id()))
                .isInstanceOf(InvalidRfqStateException.class)
                .extracting("code").isEqualTo("RFQ_NOT_OPEN");
    }

    // ---- cancel ----

    @Test
    void cancel_openRfq_transitionsAndBlocksEverything() {
        Rfq rfq = rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 10)));

        Rfq cancelled = rfqService.cancel(ownerUserId, rfq.id());
        assertThat(cancelled.status()).isEqualTo(RfqStatus.CANCELLED);

        assertThatThrownBy(() -> rfqService.cancel(ownerUserId, rfq.id()))
                .isInstanceOf(InvalidRfqStateException.class);
        assertThatThrownBy(() -> rfqService.convert(ownerUserId, rfq.id(), UUID.randomUUID()))
                .isInstanceOf(InvalidRfqStateException.class);
    }

    // ---- conversion ----

    @Test
    void convert_wrongRfqQuote_throwsNotFound() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);
        RfqQuote quote = quote(rfq, vendor);
        Rfq otherRfq = openRfqWithVendor(vendor);
        RfqQuote otherQuote = quote(otherRfq, vendor);

        assertThatThrownBy(() -> rfqService.convert(ownerUserId, rfq.id(), otherQuote.id()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("QUOTE_NOT_FOUND");
        assertThat(rfqService.get(ownerUserId, rfq.id()).status()).isEqualTo(RfqStatus.OPEN);
        assertThat(rfqService.get(ownerUserId, otherRfq.id()).status()).isEqualTo(RfqStatus.OPEN);
    }

    @Test
    void convert_happyPath_createsB2bDraftCartAndMarksConverted() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);
        RfqQuote quote = quote(rfq, vendor);

        RfqService.ConversionResult result = rfqService.convert(ownerUserId, rfq.id(), quote.id());

        assertThat(result.rfq().status()).isEqualTo(RfqStatus.CONVERTED);
        Cart cart = cartRepository.findById(result.cartId()).orElseThrow();
        assertThat(cart.companyId()).isEqualTo(companyId);
        assertThat(cart.projectId()).isEqualTo(rfq.id());
        assertThat(cart.userId()).isEqualTo(ownerUserId);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).productId()).isEqualTo(productId);
        assertThat(cart.items().get(0).quantity()).isEqualTo(100);

        // Conversion is terminal.
        assertThatThrownBy(() -> rfqService.convert(ownerUserId, rfq.id(), quote.id()))
                .isInstanceOf(InvalidRfqStateException.class)
                .extracting("code").isEqualTo("RFQ_NOT_OPEN");
    }

    @Test
    void convert_expiredQuote_rejected422() {
        Vendor vendor = vendorAdminService.create("V", List.of(categoryId));
        Rfq rfq = openRfqWithVendor(vendor);
        // Valid at submission, already stale on arrival: valid_until in the past is
        // rejected at submission, so simulate expiry by backdating after insert.
        RfqQuote quote = quote(rfq, vendor);
        jdbcTemplate.update("UPDATE rfq_quotes SET valid_until = now() - interval '1 hour' WHERE id = ?",
                quote.id());

        assertThatThrownBy(() -> rfqService.convert(ownerUserId, rfq.id(), quote.id()))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code").isEqualTo("QUOTE_EXPIRED");
        assertThat(rfqService.get(ownerUserId, rfq.id()).status()).isEqualTo(RfqStatus.OPEN);

        Integer carts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE project_id = ? AND cart_type = 'B2B_DRAFT'",
                Integer.class, rfq.id());
        assertThat(carts).isZero();
    }

    // ---- helpers ----

    /** RFQ over the cement product; the vendor's category match puts it in the routing snapshot. */
    private Rfq openRfqWithVendor(Vendor vendor) {
        return rfqService.create(ownerUserId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 100)));
    }

    private RfqQuote quote(Rfq rfq, Vendor vendor) {
        return rfqService.submitQuote(rfq.id(), vendor.id(), new BigDecimal("150.00"),
                Instant.now().plusSeconds(7200));
    }

    /** Bypasses creation validation on purpose: an OPEN row that is already due. */
    private Rfq openRfqExpiringAt(Instant expiresAt) {
        UUID rfqId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO rfqs (id, company_id, created_by_user_id, status, expires_at) "
                        + "VALUES (?, ?, ?, 'OPEN', ?)",
                rfqId, companyId, ownerUserId, java.sql.Timestamp.from(expiresAt));
        return rfqService.get(ownerUserId, rfqId);
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }

    private UUID newCategory(String slug) {
        Category category = new Category();
        category.setName(slug);
        category.setSlug(slug + "-" + UUID.randomUUID());
        return categoryRepository.save(category).getId();
    }

    private UUID newProduct(String slug, UUID categoryId) {
        Product product = new Product();
        product.setName(slug);
        product.setSlug(slug + "-" + UUID.randomUUID());
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        Product saved = productRepository.save(product);
        productBasePriceRepository.save(saved.getId(), new BigDecimal("10.00"));
        return saved.getId();
    }
}
