package com.builddash.backend.application.impl;

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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H1.1 on real Postgres: two concurrent SUCCESS deliveries for the same refund must
 * produce exactly one GST credit note and exactly one REFUND_COMPLETED transition. The
 * RETURN -> REFUND row lock (not the uq_gst_notes_return_type backstop) is what makes
 * the second delivery an idempotent no-op.
 */
class RefundWebhookDuplicateSuccessRaceJpaIT extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Autowired
    private RefundWebhookService refundWebhookService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID returnId;
    private String gatewayRefundId;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);

        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111102");
        UUID lockId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 1344.00, 'DELIVERED', now(), now())",
                orderId, userId, addressId, slotId, lockId);
        jdbcTemplate.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total, created_at, updated_at) VALUES (?, ?, ?, 3, 350.00, 294.00, 1050.00, now(), now())",
                UUID.randomUUID(), orderId, productId);
        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) VALUES (?, ?, ?, 1344.00, 'SUCCESS', now(), now())",
                UUID.randomUUID(), orderId, "tx_it_" + orderId);

        returnId = UUID.randomUUID();
        // Return already in REFUND_INITIATED: finalizeClaim already ran, the webhook is
        // the only thing left to complete the return and generate the credit note.
        jdbcTemplate.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys, created_at, updated_at) VALUES (?, ?, ?, 'REFUND_INITIATED', 'DAMAGED', '[]'::jsonb, now(), now())",
                returnId, orderId, userId);
        jdbcTemplate.update("INSERT INTO return_line_items (id, return_id, product_id, quantity_requested, refund_amount, created_at, updated_at) VALUES (?, ?, ?, 1, 350.00, now(), now())",
                UUID.randomUUID(), returnId, productId);

        gatewayRefundId = "gw_race_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status, gateway_refund_id, created_at, updated_at) VALUES (?, ?, ?, 350.00, 'PENDING', ?, now(), now())",
                UUID.randomUUID(), returnId, "tx_it_" + orderId, gatewayRefundId);
    }

    @Test
    void concurrentSuccessDeliveries_produceExactlyOneCreditNote() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        String signature = sign(returnId + ":" + gatewayRefundId + ":SUCCESS");

        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    refundWebhookService.handleWebhook(returnId, gatewayRefundId, "SUCCESS", signature);
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Integer noteCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM gst_notes WHERE return_id = ?", Integer.class, returnId);
        assertThat(noteCount).isEqualTo(1);

        String refundStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM refunds WHERE return_id = ?", String.class, returnId);
        assertThat(refundStatus).isEqualTo("SUCCESS");

        String returnStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM returns WHERE id = ?", String.class, returnId);
        assertThat(returnStatus).isEqualTo("REFUND_COMPLETED");
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
