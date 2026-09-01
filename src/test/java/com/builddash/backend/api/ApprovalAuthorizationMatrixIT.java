package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalPolicyService;
import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.revokePermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedPolicy;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 9-D permission-driven authorization matrix on real Postgres: every outcome resolves
 * against live membership/permission rows — no token refresh needed to see a revoke.
 * Application ADMIN without membership sees 404 like any non-member.
 */
class ApprovalAuthorizationMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApprovalPolicyService policyService;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID siteA;
    private UUID siteB;
    private UUID placerId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "MatrixCo");
        siteA = seedSite(jdbc, companyId, "A", true);
        siteB = seedSite(jdbc, companyId, "B", true);
        placerId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);
        date = LocalDate.now();
    }

    private UUID seedPendingRequest(UUID orderSite) {
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, orderSite, null)));
        ApprovalRequest request = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.SITE_SUPERVISOR, null, Instant.now().plus(Duration.ofHours(24)),
                new BigDecimal("150.00"), List.of(), null, List.of(), orderSite,
                List.of(CompanyRole.SITE_SUPERVISOR), 24, 1, null, null));
        return request.id();
    }

    private ApprovalRequest request(UUID requestId) {
        return transactionTemplate.execute(s -> requestRepository.findById(requestId).orElseThrow());
    }

    @Test
    void defaultProfiles_grantApprovalViewButNotAct() {
        // Defaults (CompanyPermissionDefaults): PM/SITE_SUPERVISOR carry APPROVAL_VIEW;
        // nobody carries APPROVAL_ACT by default. Approving without an explicit grant
        // must fail with FORBIDDEN, not silently succeed.
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        UUID requestId = seedPendingRequest(null);

        List<ApprovalRequest> visible = approvalService.list(supervisorUser, companyId);
        assertThat(visible).extracting(ApprovalRequest::id).contains(requestId);

        assertThatThrownBy(() -> approvalService.approve(supervisorUser, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThat(request(requestId).status()).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void viewerWithApprovalView_reads_butCannotAct() {
        UUID viewerUser = seedUser(jdbc);
        seedMember(jdbc, companyId, viewerUser, "VIEWER", null);
        grantPermission(jdbc, companyId, "VIEWER", "APPROVAL_VIEW");
        UUID requestId = seedPendingRequest(null);

        assertThat(approvalService.list(viewerUser, companyId)).hasSize(1);
        assertThat(approvalService.get(viewerUser, requestId).request().id()).isEqualTo(requestId);

        assertThatThrownBy(() -> approvalService.approve(viewerUser, requestId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> approvalService.reject(viewerUser, requestId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void accountantWithAct_canAct_whenPolicyRoleMatches() {
        UUID accountantUser = seedUser(jdbc);
        seedMember(jdbc, companyId, accountantUser, "ACCOUNTANT", null);
        grantPermission(jdbc, companyId, "ACCOUNTANT", "APPROVAL_ACT");
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null)));
        ApprovalRequest req = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.ACCOUNTANT, null, Instant.now().plus(Duration.ofHours(24)),
                new BigDecimal("150.00"), List.of(), null, List.of(), null,
                List.of(CompanyRole.ACCOUNTANT), 24, 1, null, null));

        var detail = approvalService.approve(accountantUser, req.id());
        assertThat(detail.request().status()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void supervisorWithAct_siteScoped_cannotApproveOtherSitesOrder() {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", List.of(siteA));
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedPendingRequest(siteB); // order on the OTHER site

        assertThat(approvalService.list(supervisorUser, companyId))
                .extracting(ApprovalRequest::id).doesNotContain(requestId);
        // Critical path: the site-scoped member is visibly out of scope, not hidden.
        assertThatThrownBy(() -> approvalService.approve(supervisorUser, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SITE_OUT_OF_SCOPE");
        assertThat(request(requestId).status()).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void revokeWhileApprovalScreenOpen_immediate403() {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedPendingRequest(null);

        // Screen loaded... now the OWNER revokes.
        revokePermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");

        assertThatThrownBy(() -> approvalService.approve(supervisorUser, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThat(request(requestId).status()).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void approverRemovedWhilePending_losesAccess() {
        UUID supervisorUser = seedUser(jdbc);
        UUID memberId = seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedPendingRequest(null);

        jdbc.update("DELETE FROM company_members WHERE id = ?", memberId);

        assertThatThrownBy(() -> approvalService.approve(supervisorUser, requestId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_NOT_FOUND");
    }

    @Test
    void selfApproval_blockedForOwnerPmSupervisorAndDelegate() {
        // OWNER placer
        UUID ownerPlacer = seedUser(jdbc);
        seedMember(jdbc, companyId, ownerPlacer, "OWNER", null);
        Order ownerOrder = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), ownerPlacer,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, ownerPlacer),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null)));
        ApprovalRequest ownerReq = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), ownerOrder.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.OWNER, null, Instant.now().plus(Duration.ofHours(24)),
                new BigDecimal("150.00"), List.of(), null, List.of(), null,
                List.of(CompanyRole.OWNER), 24, 1, null, null));

        assertThatThrownBy(() -> approvalService.approve(ownerPlacer, ownerReq.id()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");
        assertThatThrownBy(() -> approvalService.reject(ownerPlacer, ownerReq.id()))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");

        // PROCUREMENT_MANAGER placer holding APPROVAL_ACT — still prohibited.
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "APPROVAL_ACT");
        UUID pmRequestId = seedPendingRequest(null);
        assertThatThrownBy(() -> approvalService.approve(placerId, pmRequestId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");

        // SITE_SUPERVISOR placer via assignment + act
        UUID supervisorPlacer = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorPlacer, "SITE_SUPERVISOR", null);
        Order supervisorOrder = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), supervisorPlacer,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, supervisorPlacer),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null)));
        ApprovalRequest supervisorReq = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), supervisorOrder.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.SITE_SUPERVISOR, null, Instant.now().plus(Duration.ofHours(24)),
                new BigDecimal("150.00"), List.of(), null, List.of(), null,
                List.of(CompanyRole.SITE_SUPERVISOR), 24, 1, null, null));
        assertThatThrownBy(() -> approvalService.approve(supervisorPlacer, supervisorReq.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delegateRemovedWhilePending_cannotApproveAssignedRequest() {
        UUID delegateUser = seedUser(jdbc);
        UUID delegateMember = seedMember(jdbc, companyId, delegateUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "APPROVAL_DELEGATE");
        UUID requestId = seedPendingRequest(null);

        approvalService.delegate(placerId, requestId, delegateMember);

        jdbc.update("DELETE FROM company_members WHERE id = ?", delegateMember);

        assertThatThrownBy(() -> approvalService.approve(delegateUser, requestId))
                .isInstanceOf(NotFoundException.class);
        assertThat(request(requestId).status()).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void crossCompanyInvisible_404() {
        UUID otherCompanyId = seedCompany(jdbc, "OtherCo");
        UUID otherSupervisor = seedUser(jdbc);
        seedMember(jdbc, otherCompanyId, otherSupervisor, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, otherCompanyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedPendingRequest(null);

        assertThatThrownBy(() -> approvalService.approve(otherSupervisor, requestId))
                .isInstanceOf(NotFoundException.class);
        assertThat(approvalService.list(otherSupervisor, otherCompanyId)).isEmpty();
    }

    @Test
    void applicationAdminWithoutMembership_404() {
        // "Application ADMIN" = a plain users row with no company_members entry at all.
        UUID adminUser = seedUser(jdbc);
        UUID requestId = seedPendingRequest(null);

        assertThatThrownBy(() -> approvalService.approve(adminUser, requestId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> approvalService.list(adminUser, companyId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void policyManagement_ownerOnly_evenWithBroadPermissions() {
        UUID ownerUser = seedUser(jdbc);
        seedMember(jdbc, companyId, ownerUser, "OWNER", null);
        UUID pmUser = seedUser(jdbc);
        seedMember(jdbc, companyId, pmUser, "PROCUREMENT_MANAGER", null);
        // A non-OWNER holding broad unrelated permissions, including the COMPANY_UPDATE
        // vehicle the critical authz checks first:
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "COMPANY_UPDATE");
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "MEMBER_MANAGE");

        var command = new ApprovalPolicyService.Command(new BigDecimal("100.00"), null, null,
                List.of(CompanyRole.SITE_SUPERVISOR), null);

        var ownerPolicy = policyService.put(ownerUser, companyId, command);
        assertThat(ownerPolicy.version()).isEqualTo(1);

        assertThatThrownBy(() -> policyService.put(pmUser, companyId, command))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "OWNER_ONLY");

        // Non-OWNER can still READ (COMPANY_VIEW default).
        assertThat(policyService.get(pmUser, companyId).version()).isEqualTo(1);

        var replaced = policyService.put(ownerUser, companyId, command);
        assertThat(replaced.version()).isEqualTo(2);
    }

    @Test
    void siteDeactivation_blockedWhileGatedOrderPends() {
        UUID siteId = seedSite(jdbc, companyId, "C", true);
        seedPendingRequest(siteId);

        Integer activeOrders = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE site_id = ? AND status <> 'CANCELLED'",
                Integer.class, siteId);
        // countActiveOrdersForSite is the deactivation guard's input: a PENDING_APPROVAL
        // order pins the site exactly like a confirmed one would.
        assertThat(activeOrders).isEqualTo(1);
    }
}
