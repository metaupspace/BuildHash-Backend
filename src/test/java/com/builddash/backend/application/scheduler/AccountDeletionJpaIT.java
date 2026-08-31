package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The full per-table classification (PLAN_PHASE8 5(d)) against real Postgres: RETAIN tables
 * intact, HARD-DELETE tables empty, users tombstoned INCLUDING the blind-index columns
 * (column-level assert, not just plaintext-facing fields), referenced addresses anonymized
 * while unreferenced ones vanish (the FK-resolution confirmed at build time), S3 return
 * photos deleted and invoice keys never touched, not-due requests ignored, re-sweep no-op.
 */
class AccountDeletionJpaIT extends AbstractIntegrationTest {

    @Autowired
    private AccountDeletionSweeper sweeper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private ObjectStorage objectStorage;

    private int count(String table, UUID userId) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return c == null ? 0 : c;
    }

    private UUID user(String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, phone, email, name) VALUES (?, ?, ?, ?)",
                id, phone, phone + "@del.test", "Deletion User");
        return id;
    }

    private UUID product() {
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, ?, ?)",
                categoryId, "Del Cat " + UUID.randomUUID(), "del-" + UUID.randomUUID());
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id) VALUES (?, ?, ?, ?)",
                productId, "Del Product " + UUID.randomUUID(), "del-" + UUID.randomUUID(), categoryId);
        return productId;
    }

    @Test
    void dueRequest_fullClassificationExecuted_retainIntact_hardDeleted_tombstoned() {
        UUID userId = user("+917800400001");
        UUID productId = product();

        // Personal data in every HARD-DELETE table.
        jdbc.update("INSERT INTO devices (id, user_id, refresh_token_hash) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, "b".repeat(64));
        jdbc.update("INSERT INTO login_events (id, user_id, event_type, ip_address) VALUES (?, ?, 'OTP', '198.51.100.9')",
                UUID.randomUUID(), userId);
        UUID unreferencedAddressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', '99 Gone St', 'Mumbai', 'MH', '400002', true)",
                unreferencedAddressId, userId);
        UUID cartId = UUID.randomUUID();
        jdbc.update("INSERT INTO carts (id, user_id) VALUES (?, ?)", cartId, userId);
        jdbc.update("INSERT INTO cart_line_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, 3)",
                UUID.randomUUID(), cartId, productId);
        jdbc.update("INSERT INTO wishlist_entries (id, user_id, product_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, productId);
        jdbc.update("INSERT INTO notify_me_subscriptions (id, product_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), productId, userId);
        jdbc.update("INSERT INTO search_queries (id, query_text, user_id) VALUES (?, 'delete cement', ?)",
                UUID.randomUUID(), userId);

        UUID slotId = UUID.randomUUID();
        jdbc.update("INSERT INTO slot_configurations (id, start_time, end_time) VALUES (?, '09:00', '11:00')", slotId);
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at) VALUES (?, ?, ?, CURRENT_DATE, now() + interval '10 minutes')",
                UUID.randomUUID(), userId, slotId);

        // Order chain (RETAIN) with its OWN referenced address (anonymized, not deleted).
        UUID orderAddressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', '1 Kept St', 'Mumbai', 'MH', '400003', true)",
                orderAddressId, userId);
        UUID orderLockId = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at) VALUES (?, ?, ?, CURRENT_DATE, now() + interval '10 minutes')",
                orderLockId, userId, slotId);
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 500.00, 'DELIVERED')",
                orderId, userId, orderAddressId, slotId, orderLockId);
        jdbc.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total) VALUES (?, ?, ?, 2, 250.00, 0.00, 500.00)",
                UUID.randomUUID(), orderId, productId);
        jdbc.update("INSERT INTO payments (id, order_id, amount, status) VALUES (?, ?, 500.00, 'SUCCESS')",
                UUID.randomUUID(), orderId);
        jdbc.update("INSERT INTO invoices (id, order_id, status, storage_key) VALUES (?, ?, 'READY', 'invoices/keep-me.pdf')",
                UUID.randomUUID(), orderId);
        jdbc.update("INSERT INTO idempotency_keys (idempotency_key, order_id) VALUES (?, ?)",
                "del-key-" + UUID.randomUUID(), orderId);

        UUID returnId = UUID.randomUUID();
        jdbc.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys) VALUES (?, ?, ?, 'APPROVED', 'DAMAGED', '[\"returns/del-p1.jpg\",\"returns/del-p2.jpg\"]'::jsonb)",
                returnId, orderId, userId);
        jdbc.update("INSERT INTO return_line_items (id, return_id, product_id, quantity_requested, refund_amount) VALUES (?, ?, ?, 1, 250.00)",
                UUID.randomUUID(), returnId, productId);
        jdbc.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status) VALUES (?, ?, 'txn-del', 250.00, 'SUCCESS')",
                UUID.randomUUID(), returnId);
        jdbc.update("INSERT INTO gst_notes (id, return_id, note_type, number, amount) VALUES (?, ?, 'CREDIT', 'CN-DEL-1', 250.00)",
                UUID.randomUUID(), returnId);

        UUID couponId = UUID.randomUUID();
        jdbc.update("INSERT INTO coupons (id, code, discount_type, discount_value, expires_at) VALUES (?, ?, 'FLAT', 50, now() + interval '30 days')",
                couponId, "DEL" + System.nanoTime() % 100000);
        jdbc.update("INSERT INTO coupon_redemptions (id, coupon_id, user_id, order_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), couponId, userId, orderId);
        jdbc.update("INSERT INTO contract_pricing (id, user_id, product_id, unit_price) VALUES (?, ?, ?, 240.00)",
                UUID.randomUUID(), userId, productId);
        jdbc.update("INSERT INTO notification_logs (id, user_id, recipient_phone, channel, event_type, reference_id) VALUES (?, ?, ?, 'SMS', 'CART_ABANDONED', ?)",
                UUID.randomUUID(), userId, "+917800400001", orderId);
        UUID ticketId = UUID.randomUUID();
        jdbc.update("INSERT INTO support_tickets (id, user_id, category, subject, sla_due_at) VALUES (?, ?, 'OTHER', 'Del ticket', now() + interval '1 day')",
                ticketId, userId);
        jdbc.update("INSERT INTO support_ticket_messages (id, ticket_id, sender_role, body) VALUES (?, ?, 'CUSTOMER', 'del message')",
                UUID.randomUUID(), ticketId);

        // A not-yet-due request for a second user: must be untouched.
        UUID otherUserId = user("+917800400002");
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', '77 Other St', 'Pune', 'MH', '411001', true)",
                UUID.randomUUID(), otherUserId);
        jdbc.update("INSERT INTO delete_requests (id, user_id, requested_at, deletion_scheduled_at, status) VALUES (?, ?, now(), now() + interval '29 days', 'PENDING')",
                UUID.randomUUID(), otherUserId);

        // The due request.
        jdbc.update("INSERT INTO delete_requests (id, user_id, requested_at, deletion_scheduled_at, status) VALUES (?, ?, now() - interval '31 days', now() - interval '1 hour', 'PENDING')",
                UUID.randomUUID(), userId);

        sweeper.sweep();

        // RETAIN — untouched (contract_pricing included: B2B company data).
        assertThat(count("orders", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM order_line_items WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
        assertThat(count("returns", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM return_line_items WHERE return_id = ?", Integer.class, returnId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, returnId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM gst_notes WHERE return_id = ?", Integer.class, returnId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM contract_pricing WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);

        // HARD-DELETE — all empty for the user.
        assertThat(count("devices", userId)).isZero();
        assertThat(count("login_events", userId)).isZero();
        assertThat(count("carts", userId)).isZero();
        assertThat(count("wishlist_entries", userId)).isZero();
        assertThat(count("notify_me_subscriptions", userId)).isZero();
        assertThat(count("search_queries", userId)).isZero();
        assertThat(count("delivery_slot_locks", userId)).isZero();
        assertThat(count("coupon_redemptions", userId)).isZero();
        assertThat(count("notification_logs", userId)).isZero();
        assertThat(count("support_tickets", userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM support_ticket_messages WHERE ticket_id = ?", Integer.class, ticketId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cart_line_items WHERE cart_id = ?", Integer.class, cartId)).isZero();

        // Addresses: unreferenced gone, order-referenced anonymized (row kept, PII columns NULL).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE id = ?", Integer.class, unreferencedAddressId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE id = ? AND line1 LIKE 'v1:%' AND line2 IS NULL AND lat IS NULL AND lng IS NULL",
                Integer.class, orderAddressId)).isEqualTo(1);

        // Users tombstone: identity AND blind-index columns NULL at the column level, row kept.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE id = ? AND phone IS NULL AND email IS NULL AND name IS NULL "
                        + "AND business_name IS NULL AND gst_number IS NULL AND google_id IS NULL "
                        + "AND phone_idx IS NULL AND email_idx IS NULL AND google_id_idx IS NULL",
                Integer.class, userId)).isEqualTo(1);

        // S3: return photos deleted exactly, invoice keys never.
        ArgumentCaptor<String> deletedKeys = ArgumentCaptor.forClass(String.class);
        verify(objectStorage, times(2)).delete(deletedKeys.capture());
        assertThat(deletedKeys.getAllValues()).containsExactlyInAnyOrder("returns/del-p1.jpg", "returns/del-p2.jpg");
        verify(objectStorage, never()).delete(startsWith("invoices/"));

        // Request lifecycle: due request PROCESSED, not-due request still PENDING.
        assertThat(jdbc.queryForObject(
                "SELECT status FROM delete_requests WHERE user_id = ?", String.class, userId)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM delete_requests WHERE user_id = ?", String.class, otherUserId)).isEqualTo("PENDING");
        assertThat(count("addresses", otherUserId)).isEqualTo(1);

        // Re-sweep: no-op — everything above still holds.
        sweeper.sweep();
        assertThat(count("orders", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE id = ? AND phone IS NULL", Integer.class, userId)).isEqualTo(1);
        verify(objectStorage, times(2)).delete(anyString());   // no new deletions on re-sweep
    }
}
