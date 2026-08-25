package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PaymentWebhookControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void handleWebhook_success_updatesOrderAndPayment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)", addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 1", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')", lockId, userId, slotId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 100, 'PAYMENT_PENDING')", orderId, userId, addressId, slotId, lockId);
        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, payment_url) VALUES (?, ?, 'tx_123', 100, 'PENDING', 'url')", paymentId, orderId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "status", "SUCCESS",
                "signature", sign(orderId + ":SUCCESS")
        ));

        mockMvc.perform(post("/api/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);

        assertThat(orderStatus).isEqualTo(OrderStatus.CONFIRMED.name());
        assertThat(paymentStatus).isEqualTo(PaymentStatus.SUCCESS.name());
    }

    @Test
    void handleWebhook_invalidSignature_rejectedAndOrderUntouched() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111102");

        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)", addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 1", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')", lockId, userId, slotId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 100, 'PAYMENT_PENDING')", orderId, userId, addressId, slotId, lockId);

        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "status", "SUCCESS",
                "signature", "deadbeef"
        ));

        mockMvc.perform(post("/api/webhooks/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo(OrderStatus.PAYMENT_PENDING.name());
    }

    /** Same scheme as PaymentWebhookServiceImpl: HMAC-SHA256 hex over payload, keyed by the test-yaml secret. */
    private static String sign(String payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    "test-only-webhook-secret-0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
