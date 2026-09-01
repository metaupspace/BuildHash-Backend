package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalEscalationService;
import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.revokePermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-D escalation on real Postgres: due-stage advance against live eligibility, blocked
 * terminal-pending state, concurrent scheduler instances, and escalation vs manual
 * approval — all under the per-request REQUIRES_NEW + row-lock + re-check protocol.
 */
class ApprovalEscalationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ApprovalEscalationService escalationService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID placerId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "EscalationCo");
        placerId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "ACCOUNTANT", null);
        date = LocalDate.now();
    }

    private UUID seedDueRequest(String... stages) {
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId),
                seedCounter(jdbc, date, 10, 0), date,
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null)));
        List<CompanyRole> stageList = java.util.Arrays.stream(stages).map(CompanyRole::valueOf).toList();
        ApprovalRequest request = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, stageList.get(0), null, Instant.now().minus(Duration.ofMinutes(5)),
                new BigDecimal("150.00"), List.of(), null, List.of(), null,
                stageList, 24, 1, null, null));
        return request.id();
    }

    private ApprovalRequest request(UUID requestId) {
        return transactionTemplate.execute(s -> requestRepository.findById(requestId).orElseThrow());
    }

    private int actionCount(UUID requestId, String type) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM approval_actions WHERE request_id = ? AND action_type = ?",
                Integer.class, requestId, type);
        return n == null ? 0 : n;
    }

    @Test
    void dueRequest_advancesOnlyToStageWithLiveEligibility() {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        UUID ownerUser = seedUser(jdbc);
        seedMember(jdbc, companyId, ownerUser, "OWNER", null);
        // Stage 1 = SITE_SUPERVISOR has members but NO APPROVAL_ACT -> skipped.
        // Stage 2 = OWNER (implicit ACT) -> eligible, and not the placer.
        UUID requestId = seedDueRequest("SITE_SUPERVISOR", "SITE_SUPERVISOR", "OWNER");

        int processed = escalationService.escalateDue();

        assertThat(processed).isEqualTo(1);
        ApprovalRequest after = request(requestId);
        assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(after.currentStageIndex()).isEqualTo(2);
        assertThat(after.currentRole()).isEqualTo(CompanyRole.OWNER);
        assertThat(after.escalationDueAt()).isAfter(Instant.now());
        assertThat(actionCount(requestId, "ESCALATED")).isEqualTo(1);
    }

    @Test
    void revokedPermissionRemovesEligibility_immediatelyWithoutTokenRefresh() {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedDueRequest("SITE_SUPERVISOR", "SITE_SUPERVISOR");

        // Approver loaded the screen, THEN the owner revoked APPROVAL_ACT — the resolver
        // reads live company_role_permissions, so stage 1 has nobody.
        revokePermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");

        assertThat(escalationService.escalateDue()).isEqualTo(1);
        ApprovalRequest after = request(requestId);
        assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(after.escalationDueAt()).isNull();                       // blocked
        assertThat(actionCount(requestId, "ESCALATION_BLOCKED")).isEqualTo(1);
        assertThat(actionCount(requestId, "ESCALATED")).isZero();
    }

    @Test
    void removedMemberSkipped_placerNeverEligible() {
        // Only one member ever held ACT; she was removed while the request pended.
        UUID supervisorUser = seedUser(jdbc);
        UUID memberId = seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedDueRequest("SITE_SUPERVISOR");
        jdbc.update("DELETE FROM company_members WHERE id = ?", memberId);

        assertThat(escalationService.escalateDue()).isEqualTo(1);
        ApprovalRequest after = request(requestId);
        assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING); // never auto-cancelled
        assertThat(after.escalationDueAt()).isNull();
        assertThat(actionCount(requestId, "ESCALATION_BLOCKED")).isEqualTo(1);
    }

    @Test
    void blockedStage_repeatsSweepWritesNothingFurther() {
        UUID requestId = seedDueRequest("SITE_SUPERVISOR"); // no later stage at all

        assertThat(escalationService.escalateDue()).isEqualTo(1);
        assertThat(escalationService.escalateDue()).isEqualTo(0); // dueAt null -> not selected

        ApprovalRequest after = request(requestId);
        assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(actionCount(requestId, "ESCALATION_BLOCKED")).isEqualTo(1); // exactly once
    }

    @Test
    void escalationClearsDelegation() {
        UUID supervisorUser = seedUser(jdbc);
        UUID supervisorMember = seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        grantPermission(jdbc, companyId, "ACCOUNTANT", "APPROVAL_DELEGATE");
        UUID requestId = seedDueRequest("SITE_SUPERVISOR", "SITE_SUPERVISOR");

        ApprovalRequest before = request(requestId);
        requestRepository.save(before.assign(supervisorMember));

        escalationService.escalateDue();

        assertThat(request(requestId).assignedMemberId()).isNull();
        assertThat(request(requestId).currentStageIndex()).isEqualTo(1);
    }

    @Test
    void concurrentSchedulerInstances_oneTransition() throws Exception {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID requestId = seedDueRequest("SITE_SUPERVISOR");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger totalProcessed = new AtomicInteger();
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> {
                        await(start);
                        return escalationService.escalateDue();
                    }),
                    pool.submit(() -> {
                        await(start);
                        return escalationService.escalateDue();
                    }));
            start.countDown();
            for (Future<Integer> f : futures) {
                totalProcessed.addAndGet(f.get(20, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly one instance performed the transition; the other observed the advanced
        // request (or not-yet-due recheck) and did nothing.
        assertThat(totalProcessed.get()).isEqualTo(1);
        assertThat(actionCount(requestId, "ESCALATION_BLOCKED")).isEqualTo(1);
        assertThat(request(requestId).escalationDueAt()).isNull();
    }

    @Test
    void escalationVsManualApproval_oneAuthoritativeOutcome() throws Exception {
        UUID supervisorUser = seedUser(jdbc);
        seedMember(jdbc, companyId, supervisorUser, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID ownerUser = seedUser(jdbc);
        seedMember(jdbc, companyId, ownerUser, "OWNER", null);
        // Stage 1 = OWNER eligible: escalation ADVANCES the stage, so a supervisor
        // approve arriving after the advance must see a role mismatch, while one
        // arriving first leaves the request terminal and the sweep a no-op.
        UUID requestId = seedDueRequest("SITE_SUPERVISOR", "OWNER");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        final Exception[] approveFailure = new Exception[1];
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> {
                        await(start);
                        escalationService.escalateDue();
                    }),
                    pool.submit(() -> {
                        await(start);
                        try {
                            approvalService.approve(supervisorUser, requestId);
                        } catch (Exception e) {
                            approveFailure[0] = e;
                        }
                    }));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        ApprovalRequest after = request(requestId);
        if (after.status() == ApprovalRequestStatus.APPROVED) {
            assertThat(orderStatus(after.orderId())).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(actionCount(requestId, "APPROVED")).isEqualTo(1);
        } else {
            // Escalation won the request row first: stage advanced, manual approve
            // failed eligibility against the NEW stage under the same locks.
            assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING);
            assertThat(after.currentStageIndex()).isEqualTo(1);
            assertThat(actionCount(requestId, "ESCALATED")).isEqualTo(1);
            assertThat(approveFailure[0]).isNotNull();
        }
    }

    private OrderStatus orderStatus(UUID orderId) {
        return transactionTemplate.execute(s -> orderRepository.findById(orderId).orElseThrow()).status();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
