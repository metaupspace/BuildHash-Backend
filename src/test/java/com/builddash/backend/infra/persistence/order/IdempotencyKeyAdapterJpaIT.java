package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyAdapterJpaIT extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyKeyRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void saveAndFind_persistsCorrectly() {
        String key = "test-key-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)", addressId, userId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, gen_random_uuid(), 100, 'PAYMENT_PENDING')", orderId, userId, addressId, UUID.randomUUID());

        adapter.save(key, orderId);

        Optional<UUID> found = adapter.findOrderId(key);
        assertThat(found).isPresent().contains(orderId);
    }
}
