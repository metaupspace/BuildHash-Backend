package com.builddash.backend.infra.persistence;

import com.builddash.backend.application.service.CompanySiteService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres proof of the site deactivation guard: a non-CANCELLED order referencing
 * the site blocks deactivation (409); no active orders (or only CANCELLED ones) permits
 * it. The row-lock serialization with 9-B/9-C association flows shares this lock point
 * and is documented on CompanySiteJpaRepository#findByIdForUpdate.
 */
class CompanySiteDeactivationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CompanySiteService siteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID adminUserId;
    private UUID siteId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", adminUserId);
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, adminUserId);
        jdbcTemplate.update("INSERT INTO company_sites (id, company_id, name) VALUES (?, ?, 'HQ')",
                siteId, companyId);
    }

    private List<B2bMembership> claim() {
        return List.of(new B2bMembership(companyId, CompanyRole.ADMIN, List.of()));
    }

    /** Minimal order row referencing the site with the given status (V11 NOT NULLs satisfied). */
    private void orderReferencingSite(String status) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'S', 'C', 'MH', '400001', now(), now())", addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111102");
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) "
                        + "VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 0) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, "
                        + "delivery_slot_lock_id, company_id, site_id) VALUES (?, ?, ?, ?, CURRENT_DATE, 100.00, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, addressId, slotId, status, UUID.randomUUID(), companyId, siteId);
    }

    @Test
    void activeOrderReferencingSite_blocksDeactivation() {
        orderReferencingSite("CONFIRMED");

        assertThatThrownBy(() -> siteService.update(companyId, siteId, adminUserId, claim(),
                null, null, false))
                .isInstanceOf(SiteInUseException.class);
    }

    @Test
    void cancelledOrderReferencingSite_doesNotBlockDeactivation() {
        orderReferencingSite("CANCELLED");

        CompanySite updated = siteService.update(companyId, siteId, adminUserId, claim(),
                null, null, false);
        assertThat(updated.active()).isFalse();
    }

    @Test
    void siteWithoutOrders_deactivates_andReactivates() {
        CompanySite deactivated = siteService.update(companyId, siteId, adminUserId, claim(),
                null, null, false);
        assertThat(deactivated.active()).isFalse();

        CompanySite reactivated = siteService.update(companyId, siteId, adminUserId, claim(),
                null, null, true);
        assertThat(reactivated.active()).isTrue();
    }
}
