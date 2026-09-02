package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H1.5 on real Postgres: a legitimate SUCCESS payment webhook and the stale-order sweep's
 * cancellation both lock the same Order row via findByIdForUpdate. Whichever commits
 * first wins deterministically; the loser must never end up as
 * "CANCELLED with no durable payment evidence" — the exact gateway-money-captured/
 * order-cancelled/webhook-ignored failure mode the fix closes.
 */
class StaleOrderSweepVsPaymentWebhookRaceJpaIT extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Autowired
    private PaymentWebhookService paymentWebhookService;
    @Autowired
    private StaleOrderSweepServiceImpl staleOrderSweepService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111104");
        UUID lockId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 500.00, 'PAYMENT_PENDING', now(), now())",
                orderId, userId, addressId, slotId, lockId);
        // H1.2 precondition: a durable Payment row must already exist for the webhook to
        // ever be allowed to confirm — this test isolates the H1.5 race specifically.
        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) VALUES (?, ?, ?, 500.00, 'PENDING', now(), now())",
                UUID.randomUUID(), orderId, "tx_race_" + orderId);
    }

    @Test
    void webhookVsSweep_neverEndsCancelledWithNoPaymentEvidence() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        String signature = sign(orderId + ":SUCCESS");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> webhookFuture = pool.submit(() -> {
                await(barrier);
                paymentWebhookService.handleWebhook(orderId, "SUCCESS", signature);
                return null;
            });
            Future<?> sweepFuture = pool.submit(() -> {
                await(barrier);
                staleOrderSweepService.sweepOrder(orderId);
                return null;
            });
            webhookFuture.get(30, TimeUnit.SECONDS);
            sweepFuture.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
        Integer successPayments = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ? AND status = 'SUCCESS'", Integer.class, orderId);

        assertThat(orderStatus).isIn("CONFIRMED", "CANCELLED");
        // The failure mode this closes: captured money silently dropped on a cancelled
        // order. Whichever side won the lock race, a durable SUCCESS payment record must
        // exist — it must never be CANCELLED with zero payment evidence.
        assertThat(successPayments).isGreaterThanOrEqualTo(1);
        if (orderStatus.equals("CANCELLED")) {
            assertThat(successPayments)
                    .as("captured payment must be recorded even when the sweep won the cancellation race")
                    .isEqualTo(1);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
