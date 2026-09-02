package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.RefundWebhookService;
import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H3.5 Real-PostgreSQL proof: a return with multiple historical refund claims
 * (e.g. initial FAILED attempt followed by a retry PENDING/SUCCESS claim)
 * resolves the latest refund via findByReturnId / findLatestByReturnId without
 * throwing Spring Data JPA NonUniqueResultException.
 */
class RefundRetryMultiRowJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RefundWebhookService refundWebhookService;

    @Autowired
    private PaymentWebhookConfig webhookConfig;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void multipleRefundRows_resolvesLatestWithoutNonUniqueResultException() {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID lockId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        orderRepository.save(new Order(orderId, userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, lockId, Instant.now(), null, null, List.of(item)));

        UUID returnId = UUID.randomUUID();
        ReturnLineItem retItem = new ReturnLineItem(UUID.randomUUID(), returnId, productId, 1, new BigDecimal("672.00"));
        Return ret = new Return(returnId, orderId, userId, ReturnStatus.REFUND_INITIATED, ReturnReason.DAMAGED,
                List.of(), List.of(retItem), Instant.now().minusSeconds(3600), Instant.now());
        returnRepository.save(ret);

        // 1. First refund claim was marked FAILED
        UUID firstRefundId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status, gateway_refund_id, created_at, updated_at) VALUES (?, ?, 'tx_1', 672.00, 'FAILED', 'gw_fail_1', now() - interval '1 hour', now() - interval '1 hour')",
                firstRefundId, returnId);

        // 2. Second refund claim is PENDING
        UUID secondRefundId = UUID.randomUUID();
        String activeGatewayRefundId = "gw_active_2";
        jdbcTemplate.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status, gateway_refund_id, created_at, updated_at) VALUES (?, ?, 'tx_1', 672.00, 'PENDING', ?, now(), now())",
                secondRefundId, returnId, activeGatewayRefundId);

        // Verify 2 refund rows exist for this return
        Integer refundCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, returnId);
        assertThat(refundCount).isEqualTo(2);

        // 3. getRefund lookup returns latest PENDING refund (not FAILED, no NonUniqueResultException)
        Optional<Refund> latestRefundOpt = returnService.getRefund(returnId);
        assertThat(latestRefundOpt).isPresent();
        assertThat(latestRefundOpt.get().id()).isEqualTo(secondRefundId);
        assertThat(latestRefundOpt.get().status()).isEqualTo(RefundStatus.PENDING);

        // 4. Webhook arrives for the active refund -> handles cleanly without cardinality crash
        String webhookStatus = "SUCCESS";
        String payload = returnId + ":" + activeGatewayRefundId + ":" + webhookStatus;
        String signature = computeHmac(payload, webhookConfig.getWebhookSecret());

        refundWebhookService.handleWebhook(returnId, activeGatewayRefundId, webhookStatus, signature);

        // 5. Assert Return is REFUND_COMPLETED and latest refund is SUCCESS
        Return finalReturn = returnRepository.findById(returnId).orElseThrow();
        assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REFUND_COMPLETED);

        Refund updatedRefund = refundRepository.findById(secondRefundId).orElseThrow();
        assertThat(updatedRefund.status()).isEqualTo(RefundStatus.SUCCESS);
    }
}
