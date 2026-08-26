package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RefundWebhookControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String sign(String payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    "test-only-webhook-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void seedOrderAndReturnData(UUID userId, UUID addressId, UUID orderId, UUID returnId, UUID refundId, String gatewayRefundId) {
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID lockId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO users (id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true) ON CONFLICT DO NOTHING", addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE') ON CONFLICT DO NOTHING", lockId, userId, slotId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 100, 'CONFIRMED') ON CONFLICT DO NOTHING", orderId, userId, addressId, slotId, lockId);
        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, payment_url) VALUES (gen_random_uuid(), ?, 'tx_test_123', 100, 'SUCCESS', 'url') ON CONFLICT DO NOTHING", orderId);

        jdbcTemplate.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys) VALUES (?, ?, ?, 'REFUND_INITIATED', 'DAMAGED', '[]'::jsonb)", returnId, orderId, userId);
        jdbcTemplate.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status, gateway_refund_id) VALUES (?, ?, 'tx_test_123', 100, 'PENDING', ?)", refundId, returnId, gatewayRefundId);
    }

    @Test
    void handleWebhook_success_updatesRefundAndReturn() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_" + UUID.randomUUID();

        seedOrderAndReturnData(userId, addressId, orderId, returnId, refundId, gatewayRefundId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "returnId", returnId,
                "gatewayRefundId", gatewayRefundId,
                "status", "SUCCESS",
                "signature", sign(returnId + ":" + gatewayRefundId + ":SUCCESS")
        ));

        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        String refundStatus = jdbcTemplate.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId);
        String returnStatus = jdbcTemplate.queryForObject("SELECT status FROM returns WHERE id = ?", String.class, returnId);

        assertThat(refundStatus).isEqualTo(RefundStatus.SUCCESS.name());
        assertThat(returnStatus).isEqualTo(ReturnStatus.REFUND_COMPLETED.name());
    }

    @Test
    void handleWebhook_invalidSignature_rejectedAndUntouched() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_" + UUID.randomUUID();

        seedOrderAndReturnData(userId, addressId, orderId, returnId, refundId, gatewayRefundId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "returnId", returnId,
                "gatewayRefundId", gatewayRefundId,
                "status", "SUCCESS",
                "signature", "bad_signature"
        ));

        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        String refundStatus = jdbcTemplate.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId);
        String returnStatus = jdbcTemplate.queryForObject("SELECT status FROM returns WHERE id = ?", String.class, returnId);

        assertThat(refundStatus).isEqualTo(RefundStatus.PENDING.name());
        assertThat(returnStatus).isEqualTo(ReturnStatus.REFUND_INITIATED.name());
    }

    @Test
    void handleWebhook_duplicateRedelivery_returnsOkWithoutReprocessing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_" + UUID.randomUUID();

        seedOrderAndReturnData(userId, addressId, orderId, returnId, refundId, gatewayRefundId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "returnId", returnId,
                "gatewayRefundId", gatewayRefundId,
                "status", "SUCCESS",
                "signature", sign(returnId + ":" + gatewayRefundId + ":SUCCESS")
        ));

        // First delivery
        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Duplicate delivery
        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        String refundStatus = jdbcTemplate.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId);
        assertThat(refundStatus).isEqualTo(RefundStatus.SUCCESS.name());
    }

    @Test
    void handleWebhook_concurrentDelivery_bothReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        String gatewayRefundId = "gw_ref_concurrent_" + UUID.randomUUID();

        seedOrderAndReturnData(userId, addressId, orderId, returnId, refundId, gatewayRefundId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "returnId", returnId,
                "gatewayRefundId", gatewayRefundId,
                "status", "SUCCESS",
                "signature", sign(returnId + ":" + gatewayRefundId + ":SUCCESS")
        ));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> callWebhook = () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/webhooks/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(callWebhook);
        Future<Integer> f2 = executor.submit(callWebhook);

        startLatch.countDown();

        int s1 = f1.get(10, TimeUnit.SECONDS);
        int s2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(s1).isEqualTo(200);
        assertThat(s2).isEqualTo(200);

        String refundStatus = jdbcTemplate.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, refundId);
        assertThat(refundStatus).isEqualTo(RefundStatus.SUCCESS.name());
    }
}
