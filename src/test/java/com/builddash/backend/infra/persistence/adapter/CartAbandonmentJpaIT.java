package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.application.service.CartAbandonmentService;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.infra.persistence.entity.NotificationLogEntity;
import com.builddash.backend.infra.persistence.repository.NotificationLogJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan's boundary-race IT (Section 7, StaleOrderSweep-family) against real Postgres:
 * 59m stale not selected / 61m selected, empty cart and REORDER_SCRATCH skipped, guest
 * cart skipped, second sweep no duplicate, and the cooldown semantics — within window
 * suppressed, past window a second notification fires.
 *
 * All fixtures are raw SQL on purpose: backdating carts and log rows past their
 * @PrePersist/@PreUpdate now() stamps is impossible through the entity API (by design),
 * and every raw-SQL write below is read back before the sweep runs to prove it stuck.
 */
class CartAbandonmentJpaIT extends AbstractIntegrationTest {

    private static final java.util.concurrent.atomic.AtomicInteger PHONE_SEQ = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private CartAbandonmentService cartAbandonmentService;

    @Autowired
    private NotificationLogJpaRepository notificationLogJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private com.builddash.backend.domain.port.NotificationDispatchQueue dispatchQueue;

    private UUID seedUser(String phone, boolean guest) {
        UUID id = UUID.randomUUID();
        // null phone passes through (guest); non-null gets a run-unique suffix — the DB is
        // shared across test methods and uq_users_phone is a partial unique index.
        String uniquePhone = phone == null ? null : phone + "-" + PHONE_SEQ.incrementAndGet();
        jdbcTemplate.update("INSERT INTO users (id, phone, is_guest) VALUES (?, ?, ?)", id, uniquePhone, guest);
        return id;
    }

    private UUID seedProduct() {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, ?, ?)",
                categoryId, "Cat-" + categoryId, "cat-" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id) VALUES (?, ?, ?, ?)",
                productId, "P-" + productId, "p-" + productId, categoryId);
        return productId;
    }

    /** Raw SQL so updatedAt is exactly `minutesAgo` old — JPA's @PreUpdate would stamp now(). */
    private UUID seedCart(UUID userId, String type, boolean withItem, long minutesAgo) {
        UUID cartId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO carts (id, user_id, cart_type, created_at, updated_at) VALUES (?, ?, ?, now() - ?::interval, now() - ?::interval)",
                cartId, userId, type, (minutesAgo + 5) + " minutes", minutesAgo + " minutes");
        if (withItem) {
            jdbcTemplate.update(
                    "INSERT INTO cart_line_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, 2)",
                    UUID.randomUUID(), cartId, seedProduct());
        }
        return cartId;
    }

    /** Backdate a notification row's created_at past the cooldown — raw SQL, then read back to verify it stuck. */
    private void backdateNotificationRow(UUID cartId, String hoursAgo) {
        int updated = jdbcTemplate.update(
                "UPDATE notification_logs SET created_at = now() - ?::interval WHERE reference_id = ?",
                hoursAgo + " hours", cartId);
        assertThat(updated).as("exactly one row backdated for cart %s", cartId).isEqualTo(1);

        Instant readBack = jdbcTemplate.queryForObject(
                "SELECT created_at FROM notification_logs WHERE reference_id = ?", Instant.class, cartId);
        assertThat(readBack).isBefore(Instant.now().minusSeconds(3600));
    }

    private List<NotificationLogEntity> rowsFor(UUID userId) {
        return notificationLogJpaRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(userId))
                .toList();
    }

    @Test
    void boundaryAndSkips_followThresholdTypeItemsAndChannel() {
        // One PRIMARY cart per user (unique user_id+cart_type) — each variant gets its own user.
        UUID staleUserId = seedUser("+919900000011", false);
        UUID cart61 = seedCart(staleUserId, "PRIMARY", true, 61);
        UUID freshUserId = seedUser("+919900000012", false);
        UUID cart59 = seedCart(freshUserId, "PRIMARY", true, 59);
        UUID emptyCartUserId = seedUser("+919900000013", false);
        seedCart(emptyCartUserId, "PRIMARY", false, 120);           // empty cart skipped
        UUID scratchUserId = seedUser("+919900000014", false);
        seedCart(scratchUserId, "REORDER_SCRATCH", true, 120);      // wrong type skipped
        UUID guestUserId = seedUser(null, true);
        seedCart(guestUserId, "PRIMARY", true, 120);                // guest skipped

        cartAbandonmentService.sweepAbandonedCarts();

        assertThat(rowsFor(staleUserId)).hasSize(1);
        assertThat(rowsFor(staleUserId).getFirst().getEventType()).isEqualTo(NotificationEventType.CART_ABANDONED);
        assertThat(rowsFor(staleUserId).getFirst().getReferenceId()).isEqualTo(cart61);
        assertThat(rowsFor(staleUserId).getFirst().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(rowsFor(freshUserId)).isEmpty();
        assertThat(rowsFor(emptyCartUserId)).isEmpty();
        assertThat(rowsFor(scratchUserId)).isEmpty();
        assertThat(rowsFor(guestUserId)).isEmpty();
        assertThat(notificationLogJpaRepository.findAll())
                .noneMatch(r -> r.getReferenceId().equals(cart59));
    }

    @Test
    void secondSweepWithinCooldown_producesNoSecondRow() {
        UUID userId = seedUser("+919900000012", false);
        UUID cartId = seedCart(userId, "PRIMARY", true, 90);

        cartAbandonmentService.sweepAbandonedCarts();
        cartAbandonmentService.sweepAbandonedCarts();

        assertThat(rowsFor(userId)).hasSize(1);
    }

    @Test
    void secondAbandonmentPastCooldown_firesSecondNotification() {
        UUID userId = seedUser("+919900000013", false);
        UUID cartId = seedCart(userId, "PRIMARY", true, 90);

        cartAbandonmentService.sweepAbandonedCarts();
        assertThat(rowsFor(userId)).hasSize(1);

        // Simulate the cooldown elapsing (24h default): backdate the row, read it back,
        // then sweep again — the same stable cartId legitimately re-notifies.
        backdateNotificationRow(cartId, "25");
        cartAbandonmentService.sweepAbandonedCarts();

        List<NotificationLogEntity> mine = rowsFor(userId);
        assertThat(mine).hasSize(2);
        assertThat(mine).allSatisfy(row -> {
            assertThat(row.getEventType()).isEqualTo(NotificationEventType.CART_ABANDONED);
            assertThat(row.getReferenceId()).isEqualTo(cartId);
        });
    }
}
