package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rolling-window proof (PLAN_PHASE8 decision 10) against real Postgres: the read filter —
 * not the nightly purge — is what enforces the window. Rows are backdated via raw SQL
 * exactly as elapsed time would. Absorbs the old IdempotencyKeyAdapterJpaIT save/find case
 * as its within-window test.
 */
class IdempotencyKeyWindowJpaIT extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyKeyRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void keyWithinWindow_returnsOriginalOrderId() {
        UUID orderId = insertOrder();
        String key = "win-key-" + UUID.randomUUID();
        adapter.save(key, orderId);

        Optional<UUID> found = adapter.findOrderId(key, Instant.now().minus(Duration.ofHours(24)));

        assertThat(found).isPresent().contains(orderId);
    }

    @Test
    void keyPastWindow_readsAsAbsent_allowsNewOrder() {
        UUID orderId = insertOrder();
        String key = "expired-key-" + UUID.randomUUID();
        adapter.save(key, orderId);
        backdate(key, 48);

        Optional<UUID> found = adapter.findOrderId(key, Instant.now().minus(Duration.ofHours(24)));

        assertThat(found).isEmpty();
    }

    @Test
    void purge_deletesOnlyExpiredRows() {
        UUID freshOrderId = insertOrder();
        UUID staleOrderId = insertOrder();
        String freshKey = "purge-fresh-" + UUID.randomUUID();
        String staleKey = "purge-stale-" + UUID.randomUUID();
        adapter.save(freshKey, freshOrderId);
        adapter.save(staleKey, staleOrderId);
        backdate(staleKey, 48);

        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        int removed = adapter.deleteCreatedBefore(cutoff);

        // Order-proof: sibling tests may have backdated rows of their own, so the count is
        // "at least mine"; the invariant is that NOTHING older than the cutoff survives.
        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(adapter.findOrderId(freshKey, cutoff)).isPresent().contains(freshOrderId);
        assertThat(countRowsOlderThan(cutoff)).isZero();
    }

    private void backdate(String key, int hours) {
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET created_at = now() - make_interval(hours => ?) WHERE idempotency_key = ?",
                hours, key);
    }

    private int countRowsOlderThan(Instant cutoff) {
        // pgjdbc can't infer a type for a bare Instant through JdbcTemplate — Timestamp maps cleanly.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idempotency_keys WHERE created_at < ?", Integer.class,
                java.sql.Timestamp.from(cutoff));
        return count == null ? 0 : count;
    }

    private UUID insertOrder() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)", addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, gen_random_uuid(), 100, 'PAYMENT_PENDING')", orderId, userId, addressId, slotId);
        return orderId;
    }
}
