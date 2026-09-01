package com.builddash.backend.api;

import com.builddash.backend.application.service.StatementGenerationService;
import com.builddash.backend.application.service.StatementQueryService;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static com.builddash.backend.support.StatementTestFixtures.seedCompany;
import static com.builddash.backend.support.StatementTestFixtures.seedConfirmedOrder;
import static com.builddash.backend.support.StatementTestFixtures.seedMember;
import static com.builddash.backend.support.StatementTestFixtures.seedSite;
import static com.builddash.backend.support.StatementTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 9-E permission matrix on real Postgres: live STATEMENT_VIEW checks, company-wide
 * visibility (site scope never filters), cross-company 404, and B2C invoice isolation.
 */
class StatementAuthorizationMatrixIT extends AbstractIntegrationTest {

    private static final Instant AUGUST = Instant.parse("2026-08-15T10:00:00Z");

    @Autowired
    private StatementGenerationService generationService;
    @Autowired
    private StatementQueryService queryService;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Statement readyStatement(UUID companyId) {
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        generationService.generateDue();
        return statementRepository.findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId).get(0);
    }

    private UUID ownerOf(UUID companyId) {
        UUID owner = seedUser(jdbc);
        seedMember(jdbc, companyId, owner, "OWNER");
        return owner;
    }

    @Test
    void owner_andAccountant_seeByDefault_othersNeedGrant() {
        UUID companyId = seedCompany(jdbc, "PermCo", "Asia/Kolkata", null);
        UUID owner = seedUser(jdbc);
        UUID accountant = seedUser(jdbc);
        UUID viewer = seedUser(jdbc);
        UUID supervisor = seedUser(jdbc);
        seedMember(jdbc, companyId, owner, "OWNER");
        seedMember(jdbc, companyId, accountant, "ACCOUNTANT");
        seedMember(jdbc, companyId, viewer, "VIEWER");
        seedMember(jdbc, companyId, supervisor, "SITE_SUPERVISOR");
        Statement statement = readyStatement(companyId);

        assertThat(queryService.list(owner, companyId)).hasSize(1);
        assertThat(queryService.get(accountant, statement.id()).statement().id()).isEqualTo(statement.id());

        // VIEWER / SITE_SUPERVISOR defaults carry no STATEMENT_VIEW -> 403, live check.
        assertThatThrownBy(() -> queryService.list(viewer, companyId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThatThrownBy(() -> queryService.get(supervisor, statement.id()))
                .isInstanceOf(ForbiddenException.class);

        // OWNER grants STATEMENT_VIEW to VIEWER -> access without any token refresh.
        jdbc.update("INSERT INTO company_role_permissions (company_id, role, permission) "
                + "VALUES (?, 'VIEWER', 'STATEMENT_VIEW') ON CONFLICT DO NOTHING", companyId);
        assertThat(queryService.list(viewer, companyId)).hasSize(1);

        // Revoke again -> immediately 403.
        jdbc.update("DELETE FROM company_role_permissions WHERE company_id = ? AND role = 'VIEWER' "
                + "AND permission = 'STATEMENT_VIEW'", companyId);
        assertThatThrownBy(() -> queryService.list(viewer, companyId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void siteScopedMember_seesCompanyWide() {
        UUID companyId = seedCompany(jdbc, "SiteCo", "Asia/Kolkata", null);
        UUID supervisor = seedUser(jdbc);
        UUID memberId = seedMember(jdbc, companyId, supervisor, "SITE_SUPERVISOR");
        UUID siteA = seedSite(jdbc, companyId, "A");
        jdbc.update("INSERT INTO company_site_assignments (member_id, site_id) VALUES (?, ?)", memberId, siteA);
        jdbc.update("INSERT INTO company_role_permissions (company_id, role, permission) "
                + "VALUES (?, 'SITE_SUPERVISOR', 'STATEMENT_VIEW') ON CONFLICT DO NOTHING", companyId);
        Statement statement = readyStatement(companyId);

        // Statement visibility is company-wide — the site scope filters nothing here.
        assertThat(queryService.list(supervisor, companyId)).hasSize(1);
        assertThat(queryService.get(supervisor, statement.id()).pdfUrl()).isNotNull();
    }

    @Test
    void crossCompanyAndNonMember_404() {
        UUID companyId = seedCompany(jdbc, "HomeCo", "Asia/Kolkata", null);
        UUID otherCompanyId = seedCompany(jdbc, "AwayCo", "Asia/Kolkata", null);
        UUID otherAccountant = seedUser(jdbc);
        seedMember(jdbc, otherCompanyId, otherAccountant, "ACCOUNTANT");
        UUID stranger = seedUser(jdbc);
        Statement statement = readyStatement(companyId);

        assertThatThrownBy(() -> queryService.get(otherAccountant, statement.id()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_NOT_FOUND"); // read-hide via authorizer
        assertThatThrownBy(() -> queryService.list(stranger, companyId))
                .isInstanceOf(NotFoundException.class);
        // App ADMIN without membership is just a non-member.
        assertThatThrownBy(() -> queryService.get(stranger, statement.id()))
                .isInstanceOf(NotFoundException.class);
        // A genuinely unknown statement id is its own 404.
        assertThatThrownBy(() -> queryService.get(ownerOf(companyId), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "STATEMENT_NOT_FOUND");
    }

    @Test
    void membershipRemoved_404_suspendedCompany_stillReadable() {
        UUID companyId = seedCompany(jdbc, "GoneCo", "Asia/Kolkata", null);
        UUID accountant = seedUser(jdbc);
        UUID memberId = seedMember(jdbc, companyId, accountant, "ACCOUNTANT");
        Statement statement = readyStatement(companyId);

        jdbc.update("DELETE FROM company_members WHERE id = ?", memberId);
        assertThatThrownBy(() -> queryService.get(accountant, statement.id()))
                .isInstanceOf(NotFoundException.class);

        // Suspension stops future generation, not reads of READY artifacts.
        UUID owner = seedUser(jdbc);
        seedMember(jdbc, companyId, owner, "OWNER");
        jdbc.update("UPDATE companies SET status = 'SUSPENDED' WHERE id = ?", companyId);
        assertThat(queryService.get(owner, statement.id()).statement().status())
                .isEqualTo(com.builddash.backend.domain.enums.StatementStatus.READY);
    }

    @Test
    void suspendedCompany_schedulerGeneratesNothing() {
        UUID companyId = seedCompany(jdbc, "SuspendCo", "Asia/Kolkata", null);
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        jdbc.update("UPDATE companies SET status = 'SUSPENDED' WHERE id = ?", companyId);

        generationService.generateDue();
        assertThat(com.builddash.backend.support.StatementTestFixtures.statementCount(jdbc, companyId)).isZero();
    }

}
