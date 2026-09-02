package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.RefundWebhookService;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.RefundReference;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
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
 * H1.4b on real Postgres: RefundServiceImpl.finalizeClaim and RefundWebhookServiceImpl's
 * duplicate/late webhook delivery both write the same Refund row. SUCCESS is monotonic —
 * whichever writer commits second must never downgrade it back to PENDING or erase the
 * gateway id the other writer recorded. The external gateway call is stubbed with a
 * CyclicBarrier so the two real Postgres transactions can be forced to interleave at the
 * exact point that matters, without touching production visibility of finalizeClaim.
 */
class RefundFinalizeVsWebhookRaceJpaIT extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Autowired
    private ReturnRepository returnRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private RefundWebhookService refundWebhookService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
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
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID lockId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 1344.00, 'DELIVERED', now(), now())",
                orderId, userId, addressId, slotId, lockId);
        jdbcTemplate.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total, created_at, updated_at) VALUES (?, ?, ?, 3, 350.00, 294.00, 1050.00, now(), now())",
                UUID.randomUUID(), orderId, productId);
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
    void finalizeRacesWebhook_successNeverDowngradedToPending() throws Exception {
        UUID returnId = insertQcReturn();
        CyclicBarrier barrier = new CyclicBarrier(2);

        // Stands in for the real DummyPaymentGatewayAdapter, which returns immediately
        // with no controllable delay — this barrier is what forces the two real Postgres
        // transactions (finalize's and the webhook's) to interleave at the exact instant
        // that exercises the race, instead of one always winning by pure timing.
        PaymentGateway barrierGateway = new PaymentGateway() {
            @Override
            public PaymentReference initiate(UUID orderId, BigDecimal amount) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public RefundReference refund(String transactionId, BigDecimal amount, UUID returnIdArg) {
                String gwId = "gw_finalize_" + UUID.randomUUID();
                await(barrier);
                return new RefundReference(gwId, "PENDING");
            }

            @Override
            public java.util.Optional<com.builddash.backend.domain.enums.PaymentStatus> queryStatus(String transactionId, UUID orderId) {
                return java.util.Optional.of(com.builddash.backend.domain.enums.PaymentStatus.SUCCESS);
            }
        };
        RefundServiceImpl raceService = new RefundServiceImpl(returnRepository, paymentRepository,
                barrierGateway, refundRepository, eventPublisher, transactionTemplate);

        String webhookGatewayRefundId = "gw_webhook_wins_" + UUID.randomUUID();
        String signature = sign(returnId + ":" + webhookGatewayRefundId + ":SUCCESS");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> finalizeFuture = pool.submit(() -> raceService.initiateRefund(returnId));
            Future<?> webhookFuture = pool.submit(() -> {
                await(barrier);
                refundWebhookService.handleWebhook(returnId, webhookGatewayRefundId, "SUCCESS", signature);
                return null;
            });
            finalizeFuture.get(30, TimeUnit.SECONDS);
            webhookFuture.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // The webhook is the only writer that can ever produce a terminal outcome here —
        // regardless of which transaction wins the RETURN->REFUND lock first, the final
        // state must be the webhook's SUCCESS, never PENDING.
        String refundStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM refunds WHERE return_id = ?", String.class, returnId);
        String gatewayRefundId = jdbcTemplate.queryForObject(
                "SELECT gateway_refund_id FROM refunds WHERE return_id = ?", String.class, returnId);
        assertThat(refundStatus).isEqualTo("SUCCESS");
        assertThat(gatewayRefundId).isEqualTo(webhookGatewayRefundId);
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
