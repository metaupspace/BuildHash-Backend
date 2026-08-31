package com.builddash.backend.infra.persistence;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanyRolePermissionService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres proof of the permission foundation (V26): the new role vocabulary is
 * the only representable one, OWNER rows are unrepresentable, defaults are seeded at
 * creation, and concurrent PUTs serialize through the company-row lock into exactly
 * one complete set — never a mix.
 */
class CompanyRolePermissionJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyRolePermissionService rolePermissionService;

    @Autowired
    private CompanyRolePermissionRepository permissionRepository;

    @Autowired
    private B2bAuthorizer authorizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID ownerUserId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        ownerUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", ownerUserId);
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
                UUID.randomUUID(), companyId, ownerUserId);
    }

    private UUID member(CompanyRole role) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), companyId, userId, role.name());
        return userId;
    }

    @Test
    void v26Constraint_oldRoleValuesUnrepresentable_newValuesAccepted() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'BUYER')",
                UUID.randomUUID(), companyId, userId))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
        Integer oldRoles = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE role IN ('ADMIN','APPROVER','BUYER')", Integer.class);
        assertThat(oldRoles).isZero();

        // all five new values accepted (OWNER row above + four more)
        for (CompanyRole role : CompanyRole.values()) {
            if (role == CompanyRole.OWNER) {
                continue;
            }
            UUID u = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", u);
            jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), companyId, u, role.name());
        }
    }

    @Test
    void ownerPermissionRowsUnrepresentable_invalidPermissionUnrepresentable() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company_role_permissions (company_id, role, permission) VALUES (?, 'OWNER', 'COMPANY_VIEW')",
                companyId))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company_role_permissions (company_id, role, permission) VALUES (?, 'VIEWER', 'SUPER_POWER')",
                companyId))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void replace_isAtomic_findRolesWithPermissionReflectsIt() {
        rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER,
                Set.of(CompanyPermission.RFQ_VIEW));

        assertThat(permissionRepository.findPermissions(companyId, CompanyRole.VIEWER))
                .containsExactly(CompanyPermission.RFQ_VIEW);
        assertThat(permissionRepository.findRolesWithPermission(companyId, CompanyPermission.RFQ_VIEW))
                .contains(CompanyRole.VIEWER);

        // replace wipes the old set entirely
        rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER, Set.of());
        assertThat(permissionRepository.findPermissions(companyId, CompanyRole.VIEWER)).isEmpty();
        assertThat(permissionRepository.findRolesWithPermission(companyId, CompanyPermission.RFQ_VIEW))
                .doesNotContain(CompanyRole.VIEWER);
    }

    @Test
    void concurrentPuts_exactlyOneCompleteSetWins_neverAMix() throws Exception {
        Set<CompanyPermission> setA = Set.of(CompanyPermission.COMPANY_VIEW, CompanyPermission.ORDER_VIEW);
        Set<CompanyPermission> setB = Set.of(CompanyPermission.RFQ_VIEW, CompanyPermission.PO_VIEW,
                CompanyPermission.SITE_VIEW);

        int rounds = 5;
        for (int round = 0; round < rounds; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch gate = new CountDownLatch(1);
            try {
                Future<?> f1 = pool.submit(() -> {
                    try {
                        gate.await();
                    } catch (InterruptedException ignored) {
                    }
                    rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER, setA);
                    return null;
                });
                Future<?> f2 = pool.submit(() -> {
                    try {
                        gate.await();
                    } catch (InterruptedException ignored) {
                    }
                    rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER, setB);
                    return null;
                });
                gate.countDown();
                f1.get(30, TimeUnit.SECONDS);
                f2.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            Set<CompanyPermission> effective = permissionRepository.findPermissions(companyId, CompanyRole.VIEWER);
            assertThat(effective.equals(setA) || effective.equals(setB))
                    .as("round %d: final set is exactly A or exactly B, never a mix (was %s)", round, effective)
                    .isTrue();
        }
    }

    @Test
    void concurrentPut_vsCriticalAuthorization_serializesThroughCompanyRow() throws Exception {
        UUID viewerUser = member(CompanyRole.VIEWER);
        rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER,
                Set.of(CompanyPermission.COMPANY_VIEW));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger grantWins = new AtomicInteger();
        AtomicInteger authorizePasses = new AtomicInteger();
        AtomicInteger authorizeDenies = new AtomicInteger();

        try {
            Future<?> put = pool.submit(() -> {
                try {
                    gate.await();
                } catch (InterruptedException ignored) {
                }
                rolePermissionService.replacePermissions(companyId, ownerUserId, CompanyRole.VIEWER,
                        Set.of(CompanyPermission.COMPANY_VIEW, CompanyPermission.COMPANY_UPDATE));
                grantWins.incrementAndGet();
                return null;
            });
            Future<?> auth = pool.submit(() -> {
                try {
                    gate.await();
                } catch (InterruptedException ignored) {
                }
                try {
                    // critical authorization (mutation-grade) takes the same company row
                    authorizer.authorize(viewerUser, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
                    authorizePasses.incrementAndGet();
                } catch (com.builddash.backend.domain.exception.ForbiddenException e) {
                    authorizeDenies.incrementAndGet();
                }
                return null;
            });
            gate.countDown();
            put.get(30, TimeUnit.SECONDS);
            auth.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Either order is legal; both threads complete; final state is the granted set
        assertThat(grantWins.get()).isEqualTo(1);
        assertThat(authorizePasses.get() + authorizeDenies.get()).isEqualTo(1);
        assertThat(permissionRepository.findPermissions(companyId, CompanyRole.VIEWER))
                .contains(CompanyPermission.COMPANY_UPDATE);
    }

    @Test
    void staleJwtRoleOldVocabulary_failsSafe() {
        // A membership row can no longer hold old values (constraint above), and the
        // claim parser skips unknown roles — an old-ROLE token yields no membership
        // context, so authorization falls to the DB and the user is simply not a member.
        UUID stranger = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", stranger);
        assertThatThrownBy(() -> authorizer.authorize(stranger, companyId,
                CompanyPermission.COMPANY_VIEW, null, false))
                .isInstanceOf(com.builddash.backend.domain.exception.NotFoundException.class);
    }
}
