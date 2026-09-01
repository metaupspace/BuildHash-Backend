package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.PoAttachmentService;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedAddress;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedProductWithCategory;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H0.4 on real Postgres: suspension is enforced at the B2bAuthorizer choke point, so
 * every critical B2B mutation inherits it, and status transitions are application-ADMIN
 * authority — the company's own OWNER can no longer suspend or un-suspend itself.
 */
class SuspendedCompanyMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyMembershipService membershipService;
    @Autowired
    private RfqService rfqService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private PoAttachmentService poAttachmentService;
    @Autowired
    private B2bAuthorizer b2bAuthorizer;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID siteId;
    private UUID ownerUserId;
    private UUID memberUserId;
    private UUID productId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "SuspendedCo");
        siteId = seedSite(jdbc, companyId, "Main", true);
        ownerUserId = seedUser(jdbc);
        seedMember(jdbc, companyId, ownerUserId, "OWNER", null);
        memberUserId = seedUser(jdbc);
        seedMember(jdbc, companyId, memberUserId, "PROCUREMENT_MANAGER", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "RFQ_CREATE");
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "PO_UPLOAD");
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "ORDER_CREATE");
        UUID[] catProduct = seedProductWithCategory(jdbc);
        productId = catProduct[0];
        date = LocalDate.now();

        // Application admin suspends — the only authority that may move status.
        companyService.updateStatus(companyId, UUID.randomUUID(), List.of("ADMIN"), CompanyStatus.SUSPENDED);
    }

    @Test
    void suspendedCompany_rfqCreate_forbidden() {
        assertThatThrownBy(() -> rfqService.create(memberUserId, companyId,
                Instant.now().plus(Duration.ofHours(1)), null,
                List.of(new RfqService.ItemCommand(productId, 1))))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
    }

    @Test
    void suspendedCompany_orderCreateChokePoint_forbidden() {
        assertThatThrownBy(() -> b2bAuthorizer.authorize(memberUserId, companyId,
                CompanyPermission.ORDER_CREATE, siteId, true))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
    }

    @Test
    void suspendedCompany_poUpload_forbidden() {
        UUID orderId = seedCompanyOrder(OrderStatus.CONFIRMED);
        MockMultipartFile file = new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4b, 0x03, 0x04});

        assertThatThrownBy(() -> poAttachmentService.upload(memberUserId, orderId, file))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
    }

    @Test
    void suspendedCompany_approvalAct_forbidden() {
        UUID requestId = seedPendingApproval();

        assertThatThrownBy(() -> approvalService.approve(memberUserId, requestId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");

        assertThat(requestStatus(requestId)).isEqualTo(ApprovalRequestStatus.PENDING);
    }

    @Test
    void suspendedCompany_membershipAdd_forbidden() {
        // The critical authorize runs before any permission logic — even the OWNER's
        // member administration is inert while suspended.
        assertThatThrownBy(() -> membershipService.addMember(companyId, ownerUserId,
                UUID.randomUUID(), CompanyRole.PROCUREMENT_MANAGER, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
    }

    @Test
    void suspendedCompany_companyUpdate_forbidden() {
        assertThatThrownBy(() -> companyService.update(companyId, memberUserId,
                "Renamed", null, null, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "COMPANY_SUSPENDED");
    }

    @Test
    void owner_cannotSuspendOrUnsuspendOwnCompany() {
        assertThatThrownBy(() -> companyService.updateStatus(companyId, ownerUserId,
                List.of("USER"), CompanyStatus.SUSPENDED))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThatThrownBy(() -> companyService.updateStatus(companyId, ownerUserId,
                List.of("USER"), CompanyStatus.ACTIVE))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        assertThat(companyStatus()).isEqualTo("SUSPENDED"); // unchanged by owner attempts
    }

    @Test
    void adminReactivates_companyOperationalAgain() {
        companyService.updateStatus(companyId, UUID.randomUUID(), List.of("ADMIN"), CompanyStatus.ACTIVE);

        assertThat(companyStatus()).isEqualTo("ACTIVE");
        Rfq rfq = rfqService.create(memberUserId, companyId,
                Instant.now().plus(Duration.ofHours(1)), null,
                List.of(new RfqService.ItemCommand(productId, 1)));
        assertThat(rfq.companyId()).isEqualTo(companyId);
    }

    private String companyStatus() {
        return jdbc.queryForObject("SELECT status FROM companies WHERE id = ?", String.class, companyId);
    }

    private ApprovalRequestStatus requestStatus(UUID requestId) {
        return transactionTemplate.execute(s ->
                requestRepository.findById(requestId).orElseThrow().status());
    }

    private UUID seedCompanyOrder(OrderStatus status) {
        return transactionTemplate.execute(status1 -> orderRepository.save(new Order(
                UUID.randomUUID(), memberUserId, seedAddress(jdbc, memberUserId),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), status, null, Instant.now(),
                null, null, List.of(), companyId, siteId, null)).id());
    }

    private UUID seedPendingApproval() {
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), memberUserId, seedAddress(jdbc, memberUserId),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, siteId, null)));
        ApprovalRequest request = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.PROCUREMENT_MANAGER, null, Instant.now().plus(Duration.ofHours(24)),
                new BigDecimal("150.00"), List.of(), null, List.of(), siteId,
                List.of(CompanyRole.PROCUREMENT_MANAGER), 24, 1, null, null));
        return request.id();
    }
}
