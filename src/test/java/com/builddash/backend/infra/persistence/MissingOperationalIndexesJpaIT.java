package com.builddash.backend.infra.persistence;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H5.1 & H5.3 Real-PostgreSQL proof:
 * 1. Operational indexes exist on notification_logs, orders, idempotency_keys, reviews, questions, answers.
 * 2. Foreign key fk_orders_slot enforces slot referential integrity.
 * 3. Dead plaintext user unique indexes (uq_users_phone/email/google_id) are dropped.
 */
class MissingOperationalIndexesJpaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void operationalIndexes_existInPostgresCatalog() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "idx_notification_logs_user",
                "idx_orders_site_id",
                "idx_orders_address_id",
                "idx_idempotency_keys_created",
                "idx_reviews_user_id",
                "idx_questions_user_id",
                "idx_answers_user_id"
        );

        // Plaintext dead indexes are dropped
        assertThat(indexes).doesNotContain(
                "uq_users_phone",
                "uq_users_email",
                "uq_users_google_id"
        );

        // Blind indexes remain
        assertThat(indexes).contains(
                "uq_users_phone_idx",
                "uq_users_email_idx",
                "uq_users_google_id_idx"
        );
    }

    @Test
    void ordersSlotFk_rejectsInvalidSlotId() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', true, now(), now())",
                addressId, userId);

        UUID nonExistentSlotId = UUID.randomUUID();

        // Inserting an order with invalid slot_id fails with FK violation
        assertThatThrownBy(() ->
                jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_DATE, 500.00, 'CONFIRMED', now(), now())",
                        UUID.randomUUID(), userId, addressId, nonExistentSlotId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
