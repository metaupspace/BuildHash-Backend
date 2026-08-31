package com.builddash.backend.api.controller;

import com.builddash.backend.domain.model.UserDataExport;
import com.builddash.backend.domain.port.UserDataExporter;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DPDP export (PLAN_PHASE8 decision 8): a full-data user populates every section with the
 * right nested children; a sparse user gets empty arrays — every section key ALWAYS present.
 * Repository-only access is proven by construction: the exporter has no JdbcTemplate.
 */
class UserDataExportJpaIT extends AbstractIntegrationTest {

    @Autowired
    private UserDataExporter exporter;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID user(String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, phone, email, name) VALUES (?, ?, ?, ?)",
                id, phone, phone + "@export.test", "Export User");
        return id;
    }

    private UUID product() {
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, ?, ?)",
                categoryId, "Export Cat " + UUID.randomUUID(), "export-" + UUID.randomUUID());
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id) VALUES (?, ?, ?, ?)",
                productId, "Export Product " + UUID.randomUUID(), "exp-" + UUID.randomUUID(), categoryId);
        return productId;
    }

    @Test
    void fullUser_everySectionPopulated_nestedChildrenCorrect() {
        UUID userId = user("+917800300001");
        UUID productId = product();

        jdbc.update("INSERT INTO devices (id, user_id, refresh_token_hash) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, "a".repeat(64));
        jdbc.update("INSERT INTO login_events (id, user_id, event_type, ip_address) VALUES (?, ?, 'OTP', '203.0.113.50')",
                UUID.randomUUID(), userId);

        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', '12 Export St', 'Mumbai', 'MH', '400001', true)",
                addressId, userId);

        UUID cartId = UUID.randomUUID();
        jdbc.update("INSERT INTO carts (id, user_id) VALUES (?, ?)", cartId, userId);
        jdbc.update("INSERT INTO cart_line_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, 2)",
                UUID.randomUUID(), cartId, productId);

        UUID slotId = UUID.randomUUID();
        jdbc.update("INSERT INTO slot_configurations (id, start_time, end_time) VALUES (?, '09:00', '11:00')", slotId);
        UUID lockId = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at) VALUES (?, ?, ?, CURRENT_DATE, now() + interval '10 minutes')",
                lockId, userId, slotId);
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 100.00, 'DELIVERED')",
                orderId, userId, addressId, slotId, lockId);
        jdbc.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total) VALUES (?, ?, ?, 1, 90.00, 10.00, 100.00)",
                UUID.randomUUID(), orderId, productId);
        jdbc.update("INSERT INTO payments (id, order_id, amount, status) VALUES (?, ?, 100.00, 'SUCCESS')",
                UUID.randomUUID(), orderId);
        jdbc.update("INSERT INTO invoices (id, order_id, status) VALUES (?, ?, 'READY')",
                UUID.randomUUID(), orderId);

        UUID returnId = UUID.randomUUID();
        jdbc.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys) VALUES (?, ?, ?, 'APPROVED', 'DAMAGED', '[\"returns/p1.jpg\"]'::jsonb)",
                returnId, orderId, userId);
        jdbc.update("INSERT INTO refunds (id, return_id, payment_transaction_id, amount, status) VALUES (?, ?, 'txn-1', 100.00, 'SUCCESS')",
                UUID.randomUUID(), returnId);
        jdbc.update("INSERT INTO gst_notes (id, return_id, note_type, number, amount) VALUES (?, ?, 'CREDIT', 'CN-1', 100.00)",
                UUID.randomUUID(), returnId);

        jdbc.update("INSERT INTO reviews (id, product_id, user_id, rating) VALUES (?, ?, ?, 5)",
                UUID.randomUUID(), productId, userId);
        UUID questionId = UUID.randomUUID();
        jdbc.update("INSERT INTO questions (id, product_id, user_id, body) VALUES (?, ?, ?, 'Export question?')",
                questionId, productId, userId);
        jdbc.update("INSERT INTO answers (id, question_id, user_id, body, source) VALUES (?, ?, ?, 'Export answer', 'VENDOR')",
                UUID.randomUUID(), questionId, userId);
        jdbc.update("INSERT INTO wishlist_entries (id, user_id, product_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, productId);
        jdbc.update("INSERT INTO notify_me_subscriptions (id, product_id, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), productId, userId);
        jdbc.update("INSERT INTO search_queries (id, query_text, user_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), "export cement", userId);

        UUID couponId = UUID.randomUUID();
        jdbc.update("INSERT INTO coupons (id, code, discount_type, discount_value, expires_at) VALUES (?, ?, 'PERCENT', 10, now() + interval '30 days')",
                couponId, "EXP" + System.nanoTime() % 100000);
        jdbc.update("INSERT INTO coupon_redemptions (id, coupon_id, user_id, order_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), couponId, userId, orderId);
        jdbc.update("INSERT INTO contract_pricing (id, user_id, product_id, unit_price) VALUES (?, ?, ?, 80.00)",
                UUID.randomUUID(), userId, productId);

        jdbc.update("INSERT INTO notification_logs (id, user_id, recipient_phone, channel, event_type, reference_id) VALUES (?, ?, ?, 'SMS', 'CART_ABANDONED', ?)",
                UUID.randomUUID(), userId, "+917800300001", orderId);

        UUID ticketId = UUID.randomUUID();
        jdbc.update("INSERT INTO support_tickets (id, user_id, category, subject, sla_due_at) VALUES (?, ?, 'OTHER', 'Export ticket', now() + interval '1 day')",
                ticketId, userId);
        jdbc.update("INSERT INTO support_ticket_messages (id, ticket_id, sender_role, body) VALUES (?, ?, 'CUSTOMER', 'export message')",
                UUID.randomUUID(), ticketId);

        UserDataExport export = exporter.export(userId);

        assertThat(export.profile().getPhone()).isEqualTo("+917800300001");   // decrypted plaintext
        assertThat(export.devices()).hasSize(1);
        assertThat(export.loginEvents()).hasSize(1);
        assertThat(export.addresses()).hasSize(1);
        assertThat(export.carts()).hasSize(1);
        assertThat(export.carts().get(0).items()).hasSize(1);
        assertThat(export.orders()).hasSize(1);
        assertThat(export.orders().get(0).order().lineItems()).hasSize(1);
        assertThat(export.orders().get(0).payments()).hasSize(1);
        assertThat(export.orders().get(0).invoice()).isNotNull();
        assertThat(export.returns()).hasSize(1);
        assertThat(export.returns().get(0).refunds()).hasSize(1);
        assertThat(export.returns().get(0).gstNotes()).hasSize(1);
        assertThat(export.reviews()).hasSize(1);
        assertThat(export.questions()).hasSize(1);
        assertThat(export.answers()).hasSize(1);
        assertThat(export.wishlistEntries()).hasSize(1);
        assertThat(export.notifyMeSubscriptions()).hasSize(1);
        assertThat(export.searchQueries()).hasSize(1);
        assertThat(export.couponRedemptions()).hasSize(1);
        assertThat(export.contractPrices()).hasSize(1);
        assertThat(export.notificationLogs()).hasSize(1);
        assertThat(export.supportTickets()).hasSize(1);
        assertThat(export.supportTickets().get(0).messages()).hasSize(1);
    }

    @Test
    void sparseUser_allSectionsPresentAsEmptyArrays() {
        UUID userId = user("+917800300002");

        UserDataExport export = exporter.export(userId);

        // Every section present, none null, all empty — sparse never means missing keys.
        assertThat(export.devices()).isNotNull().isEmpty();
        assertThat(export.loginEvents()).isNotNull().isEmpty();
        assertThat(export.addresses()).isNotNull().isEmpty();
        assertThat(export.carts()).isNotNull().isEmpty();
        assertThat(export.orders()).isNotNull().isEmpty();
        assertThat(export.returns()).isNotNull().isEmpty();
        assertThat(export.reviews()).isNotNull().isEmpty();
        assertThat(export.questions()).isNotNull().isEmpty();
        assertThat(export.answers()).isNotNull().isEmpty();
        assertThat(export.wishlistEntries()).isNotNull().isEmpty();
        assertThat(export.notifyMeSubscriptions()).isNotNull().isEmpty();
        assertThat(export.searchQueries()).isNotNull().isEmpty();
        assertThat(export.couponRedemptions()).isNotNull().isEmpty();
        assertThat(export.contractPrices()).isNotNull().isEmpty();
        assertThat(export.notificationLogs()).isNotNull().isEmpty();
        assertThat(export.supportTickets()).isNotNull().isEmpty();
    }
}
