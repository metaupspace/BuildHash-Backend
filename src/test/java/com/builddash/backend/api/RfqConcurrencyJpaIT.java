package com.builddash.backend.api;

import com.builddash.backend.application.scheduler.RfqExpirySweeper;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.application.service.VendorAdminService;
import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.domain.exception.DuplicateQuoteException;
import com.builddash.backend.domain.exception.InvalidRfqStateException;
import com.builddash.backend.domain.exception.QuoteValidationException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
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
 * Real-Postgres proof of the 9-B races (Testcontainers, no mocks): the quote
 * UNIQUE constraint under an 8-way duplicate storm, quote vs sweeper, quote vs
 * conversion, two concurrent conversions, and two simultaneous sweeps. Quote
 * submission and conversion serialize on the RFQ pessimistic row lock; the
 * sweeper relies on its conditional UPDATE alone.
 */
class RfqConcurrencyJpaIT extends AbstractIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private UUID ownerUserId;
    private UUID companyId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        ownerUserId = newUser();
        companyId = companyService.create(ownerUserId, "RaceCo", null, null, null).id();

        Category category = new Category();
        category.setName("race-cat");
        category.setSlug("race-" + UUID.randomUUID());
        categoryId = categoryRepository.save(category).getId();

        Product product = new Product();
        product.setName("race-product");
        product.setSlug("race-p-" + UUID.randomUUID());
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        productId = productRepository.save(product).getId();
        productBasePriceRepository.save(productId, new BigDecimal("10.00"));
    }

    @Test
    void eightDuplicateSubmissions_exactlyOneInserts() throws Exception {
        Vendor vendor = vendorAdminService.create("Solo", List.of(categoryId));
        Rfq rfq = newRfq();
        int threads = 8;

        List<Object> outcomes = runConcurrently(threads,
                () -> rfqService.submitQuote(rfq.id(), vendor.id(), new BigDecimal("100.00"),
                        Instant.now().plusSeconds(3600)));

        long successes = outcomes.stream().filter(o -> o instanceof RfqQuote).count();
        long duplicates = outcomes.stream().filter(o -> o instanceof DuplicateQuoteException).count();
        assertThat(successes).isEqualTo(1);
        assertThat(duplicates).isEqualTo(threads - 1);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_quotes WHERE rfq_id = ?", Integer.class, rfq.id());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void quoteVersusExpirySweep_stateTransitionIsSourceOfTruth() throws Exception {
        Vendor vendor = vendorAdminService.create("Racy", List.of(categoryId));
        // OPEN and already due: whoever takes the RFQ row first decides.
        Rfq rfq = openRfqExpiredInDb(vendor.id());

        List<Object> outcomes = runConcurrently(2,
                () -> rfqService.submitQuote(rfq.id(), vendor.id(), new BigDecimal("100.00"),
                        Instant.now().plusSeconds(3600)),
                rfqExpirySweeper::sweep);

        Object quoteOutcome = outcomes.get(0);
        // Whichever way the race lands, the final state must be EXPIRED and consistent:
        // sweep won -> quote rejected with RFQ_NOT_OPEN and nothing inserted;
        // quote won -> quote committed first, then the sweep expired the RFQ.
        Integer quotes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_quotes WHERE rfq_id = ?", Integer.class, rfq.id());
        String status = status(rfq.id());
        assertThat(status).isEqualTo("EXPIRED");
        if (quoteOutcome instanceof InvalidRfqStateException) {
            assertThat(((Exception) quoteOutcome).getMessage()).contains("not open");
            assertThat(quotes).isZero();
        } else {
            assertThat(quoteOutcome).isInstanceOf(RfqQuote.class);
            assertThat(quotes).isEqualTo(1); // historical quote retained through expiry
        }
    }

    @Test
    void quoteVersusConversion_serializedByRfqLock() throws Exception {
        Vendor a = vendorAdminService.create("A", List.of(categoryId));
        Vendor b = vendorAdminService.create("B", List.of(categoryId));
        Rfq rfq = newRfq();
        RfqQuote existing = rfqService.submitQuote(rfq.id(), a.id(),
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600));

        List<Object> outcomes = runConcurrently(2,
                () -> rfqService.submitQuote(rfq.id(), b.id(), new BigDecimal("80.00"),
                        Instant.now().plusSeconds(3600)),
                () -> rfqService.convert(ownerUserId, rfq.id(), existing.id()));

        Object quoteOutcome = outcomes.get(0);
        Object convertOutcome = outcomes.get(1);
        String status = status(rfq.id());

        if (convertOutcome instanceof RfqService.ConversionResult) {
            assertThat(status).isEqualTo("CONVERTED");
            if (quoteOutcome instanceof InvalidRfqStateException) {
                // Conversion committed first: late quote rejected.
                assertThat(quoteCount(rfq.id())).isEqualTo(1);
            } else {
                // Quote committed first: conversion then flipped the RFQ — both survive.
                assertThat(quoteOutcome).isInstanceOf(RfqQuote.class);
                assertThat(quoteCount(rfq.id())).isEqualTo(2);
            }
        } else {
            // Should not happen: conversion only fails if the RFQ left OPEN, and only
            // the quote (which does not close it) races it.
            throw new AssertionError("Unexpected conversion outcome: " + convertOutcome);
        }
    }

    @Test
    void twoConcurrentConversions_exactlyOneCartAndWinner() throws Exception {
        Vendor vendor = vendorAdminService.create("Single", List.of(categoryId));
        Rfq rfq = newRfq();
        RfqQuote quote = rfqService.submitQuote(rfq.id(), vendor.id(),
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600));

        List<Object> outcomes = runConcurrently(2,
                () -> rfqService.convert(ownerUserId, rfq.id(), quote.id()),
                () -> rfqService.convert(newUserMemberOwner(), rfq.id(), quote.id()));

        long successes = outcomes.stream().filter(o -> o instanceof RfqService.ConversionResult).count();
        long notOpen = outcomes.stream().filter(o ->
                o instanceof InvalidRfqStateException e && "RFQ_NOT_OPEN".equals(e.getCode())).count();
        assertThat(successes).isEqualTo(1);
        assertThat(notOpen).isEqualTo(1);
        assertThat(status(rfq.id())).isEqualTo("CONVERTED");

        Integer carts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE project_id = ? AND cart_type = 'B2B_DRAFT'",
                Integer.class, rfq.id());
        assertThat(carts).isEqualTo(1); // no partial or duplicate cart
        Integer cartItems = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cart_line_items cli JOIN carts c ON c.id = cli.cart_id "
                        + "WHERE c.project_id = ? AND c.cart_type = 'B2B_DRAFT'",
                Integer.class, rfq.id());
        assertThat(cartItems).isEqualTo(1);
    }

    @Test
    void twoSimultaneousSweeps_expireEachDueRfqExactlyOnce() throws Exception {
        Rfq first = openRfqExpiredInDb(null);
        Rfq second = openRfqExpiredInDb(null);
        Rfq alive = newRfq();

        List<Object> outcomes = runConcurrently(2, rfqExpirySweeper::sweep);

        int totalExpired = outcomes.stream().mapToInt(o -> (Integer) o).sum();
        assertThat(totalExpired).isGreaterThanOrEqualTo(2);
        assertThat(status(first.id())).isEqualTo("EXPIRED");
        assertThat(status(second.id())).isEqualTo("EXPIRED");
        assertThat(status(alive.id())).isEqualTo("OPEN");
        // Converged, not double-counted: total affected rows equals distinct due rows.
        Integer openLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfqs WHERE status = 'OPEN' AND expires_at <= now()",
                Integer.class);
        assertThat(openLeft).isZero();
    }

    /**
     * Committed-state observation (locked OQ-2): no cross-aggregate locking between
     * Vendor and RFQ. Once vendor deactivation COMMITS, new quotes are rejected —
     * before that commit they may still succeed. Historical routes, historical
     * quotes and RFQ state are untouched by deactivation.
     */
    @Test
    void vendorDeactivation_blocksOnlyNewQuotes_afterCommit() throws Exception {
        Vendor vendor = vendorAdminService.create("Flaky", List.of(categoryId));
        Vendor other = vendorAdminService.create("Other", List.of(categoryId));
        Rfq rfq = newRfq();
        RfqQuote historical = rfqService.submitQuote(rfq.id(), vendor.id(),
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600));

        vendorAdminService.update(vendor.id(), null, null, false); // deactivation commits here

        // New quote from the deactivated vendor: rejected against committed state.
        // (The vendor is still in the creation-time routing snapshot — that is the point.)
        try {
            rfqService.submitQuote(rfq.id(), vendor.id(), new BigDecimal("90.00"),
                    Instant.now().plusSeconds(3600));
            throw new AssertionError("Expected VENDOR_INACTIVE after deactivation commit");
        } catch (QuoteValidationException expected) {
            assertThat(expected.getCode()).isEqualTo("VENDOR_INACTIVE");
        }

        // History intact: route row, historical quote, RFQ still OPEN.
        Integer routes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_routes WHERE rfq_id = ? AND vendor_id = ?",
                Integer.class, rfq.id(), vendor.id());
        assertThat(routes).isEqualTo(1);
        Integer quotes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_quotes WHERE rfq_id = ? AND vendor_id = ?",
                Integer.class, rfq.id(), vendor.id());
        assertThat(quotes).isEqualTo(1);
        assertThat(status(rfq.id())).isEqualTo("OPEN");

        // Still-routed active vendors keep quoting: deactivation is per-vendor.
        RfqQuote unaffected = rfqService.submitQuote(rfq.id(), other.id(),
                new BigDecimal("80.00"), Instant.now().plusSeconds(3600));
        assertThat(unaffected).isNotNull();
    }

    // ---- helpers ----

    /**
     * Runs {@code threads} pool threads on a barrier; task i runs tasks[i % tasks.length],
     * so an 8-way storm passes one task and a 2-way race passes two. Returns each
     * thread's outcome — the callable's value or its exception, never a throw.
     */
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

    private Rfq newRfq() {
        return rfqService.create(ownerUserId, companyId, Instant.now().plusSeconds(3600), null,
                List.of(new RfqService.ItemCommand(productId, 100)));
    }

    /** OPEN row with expires_at in the past, straight into the DB (validation bypass on purpose). */
    private Rfq openRfqExpiredInDb(UUID routedVendorId) {
        UUID rfqId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO rfqs (id, company_id, created_by_user_id, status, expires_at) "
                        + "VALUES (?, ?, ?, 'OPEN', now() - interval '1 minute')",
                rfqId, companyId, ownerUserId);
        if (routedVendorId != null) {
            jdbcTemplate.update("INSERT INTO rfq_routes (rfq_id, vendor_id) VALUES (?, ?)",
                    rfqId, routedVendorId);
        }
        return rfqService.get(ownerUserId, rfqId);
    }

    /** A second company OWNER-capable member: distinct converter identity for the race. */
    private UUID newUserMemberOwner() {
        UUID userId = newUser();
        jdbcTemplate.update(
                "INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
                UUID.randomUUID(), companyId, userId);
        return userId;
    }

    private String status(UUID rfqId) {
        return jdbcTemplate.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, rfqId);
    }

    private int quoteCount(UUID rfqId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rfq_quotes WHERE rfq_id = ?", Integer.class, rfqId);
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }
}
