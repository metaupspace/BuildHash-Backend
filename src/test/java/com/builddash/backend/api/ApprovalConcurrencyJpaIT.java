package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.exception.InvalidApprovalStateException;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
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
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedPolicy;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-D worst-case races on real Postgres. Every scenario asserts deterministic
 * terminal state: exactly one winning mutation, losers observing terminal state,
 * no duplicate actions (UNIQUE(request_id, action_type, stage_index) backstop),
 * exactly one payment initiation and one slot allocation.
 */
class ApprovalConcurrencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private ApprovalActionRepository actionRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StaleOrderSweepService staleOrderSweepService;
    @Autowired
    private com.builddash.backend.application.service.OrderService orderService;
    @Autowired
    private com.builddash.backend.application.service.OrderTrackingService orderTrackingService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID placerId;
    private UUID approverId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "RaceCo");
        placerId = seedUser(jdbc);
        approverId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);
        seedMember(jdbc, companyId, approverId, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        date = LocalDate.now();
    }

    /**
     * Gated order + PENDING request at stage 0 = SITE_SUPERVISOR, slot counter fresh.
     * Placer is a parameter: acquireOrSwapLock REUSES one ACTIVE lock per user+slot+date,
     * so capacity contention between two approvals needs two different placers.
     */
    private UUID[] seedGatedPair(UUID userId, UUID slotId, BigDecimal total) {
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), userId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, userId), slotId, date, total,
                OrderStatus.PENDING_APPROVAL, null, Instant.now(), null, null, List.of(),
                companyId, null, null)));
        ApprovalRequest request = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.SITE_SUPERVISOR, null, Instant.now().plus(Duration.ofHours(24)),
                total, List.of(), null, List.of(), null,
                List.of(CompanyRole.SITE_SUPERVISOR, CompanyRole.OWNER), 24, 1, null, null));
        return new UUID[]{order.id(), request.id()};
    }

    /** Runs two racing callables; returns per-outcome counts via supplied counters. */
    private void race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> {
                        await(start);
                        a.run();
                    }),
                    pool.submit(() -> {
                        await(start);
                        b.run();
                    }));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Outcome(AtomicInteger ok, AtomicInteger rejected) {
        Outcome() {
            this(new AtomicInteger(), new AtomicInteger());
        }

        void runChecked(Runnable action) {
            try {
                action.run();
                ok.incrementAndGet();
            } catch (DomainException e) {
                rejected.incrementAndGet();
            }
        }
    }

    private Order order(UUID orderId) {
        return transactionTemplate.execute(s -> orderRepository.findById(orderId).orElseThrow());
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
    void approveVsApprove_exactlyOneWinner() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, pair[1])),
             () -> o.runChecked(() -> approvalService.approve(approverId, pair[1])));

        assertThat(o.ok().get()).isEqualTo(1);
        assertThat(o.rejected().get()).isEqualTo(1);
        assertThat(request(pair[1]).status()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(actionCount(pair[1], "APPROVED")).isEqualTo(1);
        assertThat(pendingPaymentRows(pair[0])).isEqualTo(1); // exactly one initiation
    }

    @Test
    void approveVsReject_oneAuthoritativeOutcome() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, pair[1])),
             () -> o.runChecked(() -> approvalService.reject(approverId, pair[1])));

        assertThat(o.ok().get()).isEqualTo(1);
        assertThat(o.rejected().get()).isEqualTo(1);
        ApprovalRequestStatus finalStatus = request(pair[1]).status();
        assertThat(finalStatus).isIn(ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.REJECTED);
        OrderStatus orderStatus = order(pair[0]).status();
        assertThat(orderStatus).isIn(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED);
        assertThat(orderStatus == OrderStatus.PAYMENT_PENDING
                ? ApprovalRequestStatus.APPROVED : ApprovalRequestStatus.REJECTED).isEqualTo(finalStatus);
    }

    @Test
    void approveVsPlacerCancel_oneWinner_coherentTerminalState() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        // Placer cancellation runs through the extended existing endpoint service.
        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, pair[1])),
             () -> o.runChecked(() -> orderTrackingService.cancelOrderWithinWindow(placerId, pair[0])));

        assertThat(o.ok().get()).isEqualTo(1);
        assertThat(o.rejected().get()).isEqualTo(1);
        ApprovalRequestStatus finalStatus = request(pair[1]).status();
        if (finalStatus == ApprovalRequestStatus.APPROVED) {
            assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        } else {
            assertThat(finalStatus).isEqualTo(ApprovalRequestStatus.CANCELLED);
            assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Test
    void approveVsPaymentRetry_retryBlockedWhileGated_recoversAfterApproval() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));

        // While gated: retry must refuse — no gateway, no payment row.
        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, pair[1])),
             () -> o.runChecked(() -> orderService.retryPayment(placerId, pair[0])));
        assertThat(o.rejected().get()).isEqualTo(1);

        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(pendingPaymentRows(pair[0])).isEqualTo(1);
        // After approval commit, retry is blocked by the existing in-flight PENDING payment,
        // never by the gate — and it never double-initiates.
        assertThat(o.ok().get()).isEqualTo(1);
    }

    @Test
    void duplicateApprovePost_secondSeesTerminalState() {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));

        approvalService.approve(approverId, pair[1]);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> approvalService.approve(approverId, pair[1]))
                .isInstanceOf(InvalidApprovalStateException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_NOT_PENDING");
        assertThat(actionCount(pair[1], "APPROVED")).isEqualTo(1);
    }

    @Test
    void delegationVsApproval_outcomeCoherent_noDuplicateActions() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        UUID delegateUserId = seedUser(jdbc);
        UUID delegateMemberId = seedMember(jdbc, companyId, delegateUserId, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "APPROVAL_DELEGATE");
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        UUID approverMemberId = jdbc.queryForObject(
                "SELECT id FROM company_members WHERE company_id = ? AND user_id = ?",
                UUID.class, companyId, approverId);

        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, pair[1])),
             () -> o.runChecked(() -> approvalService.delegate(placerId, pair[1], delegateMemberId)));

        // Whatever interleaving: at most one delegation, at most one decision, coherent state.
        assertThat(actionCount(pair[1], "DELEGATED")).isLessThanOrEqualTo(1);
        assertThat(actionCount(pair[1], "APPROVED")).isLessThanOrEqualTo(1);
        ApprovalRequest after = request(pair[1]);
        if (after.status() == ApprovalRequestStatus.APPROVED) {
            assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        } else {
            assertThat(after.status()).isEqualTo(ApprovalRequestStatus.PENDING);
            assertThat(after.assignedMemberId()).isEqualTo(delegateMemberId);
        }
        // No re-delegation after a delegation landed.
        if (after.assignedMemberId() != null && after.status() == ApprovalRequestStatus.PENDING) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> approvalService.delegate(placerId, pair[1], approverMemberId))
                    .isInstanceOf(InvalidApprovalStateException.class);
        }
    }

    @Test
    void duplicateDelegatePost_secondRejected() {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        UUID delegateUserId = seedUser(jdbc);
        UUID delegateMemberId = seedMember(jdbc, companyId, delegateUserId, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "APPROVAL_DELEGATE");
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");

        approvalService.delegate(placerId, pair[1], delegateMemberId);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> approvalService.delegate(placerId, pair[1], delegateMemberId))
                .isInstanceOf(InvalidApprovalStateException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_ALREADY_DELEGATED");
        assertThat(actionCount(pair[1], "DELEGATED")).isEqualTo(1);
    }

    @Test
    void slotContention_capacityOne_exactlyOneAllocation() throws Exception {
        UUID slotId = seedCounter(jdbc, date, 1, 0); // capacity 1
        UUID secondPlacerId = seedUser(jdbc);
        seedMember(jdbc, companyId, secondPlacerId, "PROCUREMENT_MANAGER", null);
        UUID[] winnerPair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));
        UUID[] loserPair = seedGatedPair(secondPlacerId, slotId, new BigDecimal("150.00"));

        Outcome o = new Outcome();
        race(() -> o.runChecked(() -> approvalService.approve(approverId, winnerPair[1])),
             () -> o.runChecked(() -> approvalService.approve(approverId, loserPair[1])));

        // Both approve calls may "succeed" logically, but only one keeps the slot; the
        // other is cancelled atomically with origin APPROVAL_SLOT_UNAVAILABLE. However the
        // SlotUnavailableException surfaces post-commit as a DomainException for the loser.
        Order winnerOrder = order(winnerPair[0]);
        Order loserOrder = order(loserPair[0]);
        long paid = 0;
        long cancelled = 0;
        if (winnerOrder.status() == OrderStatus.PAYMENT_PENDING) {
            paid++;
        }
        if (loserOrder.status() == OrderStatus.PAYMENT_PENDING) {
            paid++;
        }
        if (winnerOrder.status() == OrderStatus.CANCELLED) {
            cancelled++;
        }
        if (loserOrder.status() == OrderStatus.CANCELLED) {
            cancelled++;
        }
        assertThat(paid).as("exactly one order resumes payment").isEqualTo(1);
        assertThat(cancelled).as("the other is cancelled").isEqualTo(1);

        assertThat(ApprovalTestFixtures.counterCount(jdbc, slotId, date))
                .as("no double allocation").isEqualTo(1);

        UUID cancelledRequestId = winnerOrder.status() == OrderStatus.CANCELLED ? winnerPair[1] : loserPair[1];
        assertThat(request(cancelledRequestId).status()).isEqualTo(ApprovalRequestStatus.CANCELLED);
        assertThat(actionCount(cancelledRequestId, "CANCELLED")).isEqualTo(1);
        String detail = jdbc.queryForObject(
                "SELECT detail FROM approval_actions WHERE request_id = ? AND action_type = 'CANCELLED'",
                String.class, cancelledRequestId);
        assertThat(detail).isEqualTo("APPROVAL_SLOT_UNAVAILABLE");

        UUID paidOrderId = winnerOrder.status() == OrderStatus.PAYMENT_PENDING ? winnerPair[0] : loserPair[0];
        assertThat(pendingPaymentRows(paidOrderId)).isEqualTo(1);
    }

    @Test
    void staleSweep_ignoresPendingApprovalOrders() {
        UUID slotId = seedCounter(jdbc, date, 10, 0);
        UUID[] pair = seedGatedPair(placerId, slotId, new BigDecimal("150.00"));

        // Backdate placedAt (orders.created_at) beyond any sweep cutoff — the order has
        // no delivery lock id and is not PAYMENT_PENDING, so both sweep selectors skip it.
        jdbc.update("UPDATE orders SET created_at = now() - interval '48 hours' WHERE id = ?", pair[0]);
        staleOrderSweepService.sweepStaleOrders();
        staleOrderSweepService.sweepExpiredLocks();

        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        assertThat(request(pair[1]).status()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(pendingPaymentRows(pair[0])).isZero();
    }

    private int pendingPaymentRows(UUID orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ? AND transaction_id IS NOT NULL",
                Integer.class, orderId);
        return n == null ? 0 : n;
    }
}
