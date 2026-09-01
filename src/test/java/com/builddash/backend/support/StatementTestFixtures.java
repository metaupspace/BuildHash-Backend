package com.builddash.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate seed helpers for the 9-E statement ITs — ApprovalTestFixtures style:
 * real Postgres rows, services exercised directly. Monetary values land as exact
 * NUMERIC(12,2) via string literals — never doubles.
 */
public final class StatementTestFixtures {

    private StatementTestFixtures() {
    }

    public static UUID seedCompany(JdbcTemplate jdbc, String name, String timezone, String statementEmail) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name, business_timezone, statement_email) VALUES (?, ?, ?, ?)",
                id, name, timezone, statementEmail);
        // Default role profiles, exactly as CompanyServiceImpl creates them — ACCOUNTANT
        // (and OWNER implicitly) carry STATEMENT_VIEW.
        for (var role : com.builddash.backend.domain.service.CompanyPermissionDefaults.customizableRoles()) {
            for (var permission : com.builddash.backend.domain.service.CompanyPermissionDefaults.forRole(role)) {
                jdbc.update("INSERT INTO company_role_permissions (company_id, role, permission) "
                        + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING", id, role.name(), permission.name());
            }
        }
        return id;
    }

    public static UUID seedSite(JdbcTemplate jdbc, UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO company_sites (id, company_id, name, active) VALUES (?, ?, ?, true)",
                id, companyId, name);
        return id;
    }

    public static UUID seedUser(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now())",
                id, "+9199" + String.format("%08d", Math.abs(id.hashCode() % 100000000)));
        return id;
    }

    public static UUID seedMember(JdbcTemplate jdbc, UUID companyId, UUID userId, String role) {
        UUID memberId = UUID.randomUUID();
        jdbc.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                memberId, companyId, userId, role);
        return memberId;
    }

    /**
     * Confirmed B2B order with one line (unit 100.00 ex-GST, tax 18.00, total 118.00).
     * confirmedAt drives the reporting period; line_total is GST-inclusive per V15.
     */
    public static UUID seedConfirmedOrder(JdbcTemplate jdbc, UUID companyId, UUID userId, UUID siteId,
                                          Instant confirmedAt, String lineTotal, String taxAmount) {
        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'S', 'C', 'MH', '400001', now(), now())", addressId, userId);
        UUID slotId = UUID.randomUUID();
        jdbc.update("INSERT INTO slot_configurations (id, start_time, end_time, capacity, is_active) "
                + "VALUES (?, '09:00', '12:00', 50, true)", slotId);
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)", categoryId, "c" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'P', ?, ?, 'ACTIVE', '2523', now(), now())", productId, "p" + productId, categoryId);
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, "
                        + "delivery_slot_lock_id, created_at, confirmed_at, company_id, site_id) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, ?::numeric, 'CONFIRMED', gen_random_uuid(), ?, ?, ?, ?)",
                orderId, userId, addressId, slotId, lineTotal,
                Timestamp.from(confirmedAt.minusSeconds(60)), Timestamp.from(confirmedAt), companyId, siteId);
        UUID lineId = UUID.randomUUID();
        jdbc.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total) "
                        + "VALUES (?, ?, ?, 1, ?::numeric, ?::numeric, ?::numeric)",
                lineId, orderId, productId, lineTotal, taxAmount, lineTotal);
        return orderId;
    }

    public static void seedInvoice(JdbcTemplate jdbc, UUID orderId, String status) {
        jdbc.update("INSERT INTO invoices (id, order_id, number, status, attempt_count) "
                + "VALUES (?, ?, ?, ?, 0)", UUID.randomUUID(), orderId,
                status.equals("READY") ? "INV-TEST-" + orderId : null, status);
    }

    /** CREDIT gst_note issued at `generatedAt` against a return on the given order. */
    public static void seedCreditNote(JdbcTemplate jdbc, UUID orderId, Instant generatedAt, String amount) {
        UUID returnId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        jdbc.update("INSERT INTO returns (id, order_id, user_id, status, reason) "
                + "VALUES (?, ?, ?, 'REFUND_COMPLETED', 'DAMAGED')", returnId, orderId, userId);
        jdbc.update("INSERT INTO gst_notes (id, return_id, note_type, number, amount, generated_at) "
                        + "VALUES (?, ?, 'CREDIT', ?, ?::numeric, ?)",
                UUID.randomUUID(), returnId, "CRN-TEST-" + returnId, amount,
                Timestamp.from(generatedAt));
    }

    public static int statementCount(JdbcTemplate jdbc, UUID companyId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM statements WHERE company_id = ?",
                Integer.class, companyId);
        return n == null ? 0 : n;
    }
}
