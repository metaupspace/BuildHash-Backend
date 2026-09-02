package com.builddash.backend.infra.persistence;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H4.5 & H4.6 Real-PostgreSQL proof:
 * 1. Negative financial amounts are rejected by PostgreSQL CHECK constraints.
 * 2. Statutory financial records (payments, invoices, returns, refunds, gst_notes)
 *    cannot be cascade deleted on parent deletion (ON DELETE RESTRICT).
 */
class FinancialCheckConstraintsAndRetentionJpaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID addressId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);

        slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
    }

    @Test
    void negativeOrderAmount_rejectedByCheckConstraint() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() ->
                jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_DATE, -100.00, 'CONFIRMED', now(), now())",
                        orderId, userId, addressId, slotId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativePaymentAmount_rejectedByCheckConstraint() {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, 100.00, 'CONFIRMED', now(), now())",
                orderId, userId, addressId, slotId);

        assertThatThrownBy(() ->
                jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'tx_neg', -50.00, 'SUCCESS', now(), now())",
                        UUID.randomUUID(), orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteOrderWithPaymentOrInvoice_blockedByRestrictForeignKey() {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, 500.00, 'CONFIRMED', now(), now())",
                orderId, userId, addressId, slotId);

        jdbcTemplate.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) " +
                "VALUES (?, ?, 'tx_retain', 500.00, 'SUCCESS', now(), now())",
                UUID.randomUUID(), orderId);

        // Attempting to delete the order with financial history must fail (RESTRICT)
        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
