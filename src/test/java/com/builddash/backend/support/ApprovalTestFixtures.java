package com.builddash.backend.support;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.service.CompanyPermissionDefaults;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * JdbcTemplate seed helpers for the 9-D approval ITs — same direct-SQL style as
 * CompanyOrderTaggingIT: real Postgres rows, no HTTP/auth plumbing, services
 * autowired and exercised directly. seedCompany mirrors CompanyServiceImpl: default
 * role-permission profiles are inserted so permission behavior matches production.
 */
public final class ApprovalTestFixtures {

    private ApprovalTestFixtures() {
    }

    public static final UUID SLOT_09_12 = UUID.fromString("11111111-1111-1111-1111-111111111101");
    public static final UUID SLOT_15_18 = UUID.fromString("11111111-1111-1111-1111-111111111103");

    /** Phone included so notification dispatch writes real log rows. */
    public static UUID seedUser(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now())",
                userId, "+9199" + String.format("%08d", Math.abs(userId.hashCode() % 100000000)));
        return userId;
    }

    public static UUID seedCompany(JdbcTemplate jdbc, String name) {
        UUID companyId = UUID.randomUUID();
        jdbc.update("INSERT INTO companies (id, name) VALUES (?, ?)", companyId, name);
        for (CompanyRole role : CompanyPermissionDefaults.customizableRoles()) {
            for (CompanyPermission permission : CompanyPermissionDefaults.forRole(role)) {
                jdbc.update("INSERT INTO company_role_permissions (company_id, role, permission) "
                        + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING", companyId, role.name(), permission.name());
            }
        }
        return companyId;
    }

    public static UUID seedSite(JdbcTemplate jdbc, UUID companyId, String name, boolean active) {
        UUID siteId = UUID.randomUUID();
        jdbc.update("INSERT INTO company_sites (id, company_id, name, active) VALUES (?, ?, ?, ?)",
                siteId, companyId, name, active);
        return siteId;
    }

    /** Inserts the membership row; returns the generated member id. */
    public static UUID seedMember(JdbcTemplate jdbc, UUID companyId, UUID userId, String role, List<UUID> siteIds) {
        UUID memberId = UUID.randomUUID();
        jdbc.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                memberId, companyId, userId, role);
        for (UUID siteId : siteIds == null ? List.<UUID>of() : siteIds) {
            jdbc.update("INSERT INTO company_site_assignments (member_id, site_id) VALUES (?, ?)",
                    memberId, siteId);
        }
        return memberId;
    }

    public static void grantPermission(JdbcTemplate jdbc, UUID companyId, String role, String permission) {
        jdbc.update("INSERT INTO company_role_permissions (company_id, role, permission) VALUES (?, ?, ?) "
                + "ON CONFLICT DO NOTHING", companyId, role, permission);
    }

    public static void revokePermission(JdbcTemplate jdbc, UUID companyId, String role, String permission) {
        jdbc.update("DELETE FROM company_role_permissions WHERE company_id = ? AND role = ? AND permission = ?",
                companyId, role, permission);
    }

    /** Serviceable address row for the given user; orders.address_id is a FK. */
    public static UUID seedAddress(JdbcTemplate jdbc, UUID userId) {
        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'S', 'C', 'MH', '400001', now(), now())", addressId, userId);
        return addressId;
    }

    /** Product with its own fresh category; returns {productId, categoryId}. */
    public static UUID[] seedProductWithCategory(JdbcTemplate jdbc) {
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)", categoryId, "c" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'P', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "p" + productId, categoryId);
        return new UUID[]{productId, categoryId};
    }

    public static void seedPolicy(JdbcTemplate jdbc, UUID companyId, BigDecimal amountThreshold,
                                  UUID[] categoryIds, UUID[] siteIds, String[] roleStages, int escalationHours, int version) {
        jdbc.update("INSERT INTO company_approval_policies "
                        + "(id, company_id, amount_threshold, category_ids, site_ids, role_stages, escalation_hours, version) "
                        + "VALUES (?, ?, ?, ?::uuid[], ?::uuid[], ?::varchar[], ?, ?)",
                companyId, companyId, amountThreshold, uuidArray(categoryIds), uuidArray(siteIds),
                varcharArray(roleStages), escalationHours, version);
    }

    public static String uuidArray(UUID[] ids) {
        if (ids == null || ids.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids[i]);
        }
        return sb.append('}').toString();
    }

    public static String varcharArray(String[] values) {
        if (values == null || values.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.append('}').toString();
    }

    /** Fresh counter row (capacity/current set by caller) on its own fresh slot config. */
    public static UUID seedCounter(JdbcTemplate jdbc, LocalDate date, int capacity, int currentCount) {
        UUID slotId = UUID.randomUUID();
        jdbc.update("INSERT INTO slot_configurations (id, start_time, end_time, capacity, is_active) "
                + "VALUES (?, '09:00', '12:00', ?, true)", slotId, capacity);
        jdbc.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, ?)", slotId, date, capacity, currentCount);
        return slotId;
    }

    /** ACTIVE delivery lock + increment, as acquireOrSwapLock would leave it. Returns lock id. */
    public static UUID seedActiveLock(JdbcTemplate jdbc, UUID userId, UUID slotId, LocalDate date) {
        UUID lockId = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) "
                + "VALUES (?, ?, ?, ?, now() + interval '15 minutes', 'ACTIVE')", lockId, userId, slotId, date);
        return lockId;
    }

    public static int counterCount(JdbcTemplate jdbc, UUID slotId, LocalDate date) {
        Integer count = jdbc.queryForObject(
                "SELECT current_count FROM delivery_slot_counters WHERE slot_id = ? AND slot_date = ?",
                Integer.class, slotId, date);
        return count == null ? -1 : count;
    }
}
