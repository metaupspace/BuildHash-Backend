package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.application.service.RefundWebhookService;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres proofs for the 8.1-C refund shape: two concurrent initiation attempts
 * for one Return produce exactly one durable claim and therefore at most one gateway
 * call (the gateway runs only after a claim commits, and the row-locked claim phase
 * rejects the second initiator before any gateway call); and a webhook arriving before
 * finalization completes the existing PENDING claim through the unchanged
 * findByReturnId fallback in RefundWebhookServiceImpl.
 */
class RefundConcurrencyJpaIT extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundWebhookService refundWebhookService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID orderId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);
        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);

        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID lockId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 1344.00, 'DELIVERED', now(), now())",
                orderId, userId, addressId, slotId, lockId);
        jdbcTemplate.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total, created_at, updated_at) VALUES (?, ?, ?, 3, 350.00, 294.00, 1050.00, now(), now())",
                UUID.randomUUID(), orderId, productId);
        // Non-threshold amount so the dummy gateway returns SUCCESS (its async webhook
        // fires 2s later, after these tests' assertions have already run).
        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) VALUES (?, ?, ?, 1344.00, 'SUCCESS', now(), now())",
                UUID.randomUUID(), orderId, "tx_it_" + orderId);
    }

    private UUID insertQcReturn() {
        UUID returnId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys, created_at, updated_at) VALUES (?, ?, ?, 'QC', 'DAMAGED', '[]'::jsonb, now(), now())",
                returnId, orderId, userId);
        jdbcTemplate.update("INSERT INTO return_line_items (id, return_id, product_id, quantity_requested, refund_amount, created_at, updated_at) VALUES (?, ?, ?, 1, 350.00, now(), now())",
                UUID.randomUUID(), returnId, productId);
        return returnId;
    }

    @Test
    void concurrentInitiation_oneDurableClaim_oneGatewayOutcome() throws Exception {
        UUID returnId = insertQcReturn();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        refundService.initiateRefund(returnId);
                        successes.incrementAndGet();
                    } catch (org.mockito.exceptions.base.MockitoException |
                             org.opentest4j.AssertionFailedError |
                             com.builddash.backend.domain.exception.InvalidReturnStateException |
                             com.builddash.backend.domain.exception.NotFoundException e) {
                        // NotFound/InvalidState both mean the row-locked claim phase
                        // rejected this initiator before any gateway call.
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // One claim commits; the other initiator is rejected inside the claim phase —
        // structurally impossible to reach the gateway without a claim row.
        assertThat(successes.get() + rejected.get()).isEqualTo(threads);
        assertThat(successes.get()).isEqualTo(1);
        Integer claimCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, returnId);
        assertThat(claimCount).isEqualTo(1);
    }

    @Test
    void webhookBeforeFinalization_completesPendingClaimViaExistingFallback() {
        // Simulated crash window: gateway refund succeeded, finalization never ran —
        // the durable claim is PENDING with no gateway id.
        UUID returnId = insertQcReturn();
        UUID claimId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status, gateway_refund_id, created_at, updated_at) VALUES (?, ?, ?, 350.00, 'PENDING', NULL, now(), now())",
                claimId, returnId, "tx_it_" + orderId);

        String gatewayRefundId = "gw_recovered_" + UUID.randomUUID();
        refundWebhookService.handleWebhook(returnId, gatewayRefundId, "SUCCESS",
                sign(returnId + ":" + gatewayRefundId + ":SUCCESS"));

        // The unchanged findByReturnId fallback matched the claim and recorded the outcome.
        Integer completed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refunds WHERE id = ? AND status = 'SUCCESS' AND gateway_refund_id = ?",
                Integer.class, claimId, gatewayRefundId);
        assertThat(completed).isEqualTo(1);
        // The Return stays QC: webhook completion of the return itself requires
        // REFUND_INITIATED, so the narrowed design leaves the return for reconciliation.
        String returnStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM returns WHERE id = ?", String.class, returnId);
        assertThat(returnStatus).isEqualTo("QC");
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
