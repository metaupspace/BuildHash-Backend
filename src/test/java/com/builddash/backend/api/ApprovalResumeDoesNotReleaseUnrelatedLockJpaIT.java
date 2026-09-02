package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
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

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2.6 on real Postgres: approval resume must take a PLAIN acquireLock — the gated
 * order's own lock was already released when approval opened, so acquireOrSwapLock had
 * nothing of the order's to swap and would instead release whatever OTHER active lock
 * the user happens to hold, silently freeing capacity that belongs to a concurrent
 * B2C checkout. One unrelated ACTIVE lock plus one gated order: after approve(), the
 * unrelated lock must be untouched (still ACTIVE, counter unchanged) and the order must
 * hold a fresh lock of its own.
 */
class ApprovalResumeDoesNotReleaseUnrelatedLockJpaIT extends AbstractIntegrationTest {

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
    private UUID approverId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "UnrelatedLockCo");
        placerId = seedUser(jdbc);
        approverId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);
        seedMember(jdbc, companyId, approverId, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        date = LocalDate.now().plusDays(1);
    }

    @Test
    void approve_acquiresFreshLock_keepsUsersUnrelatedLockIntact() {
        UUID orderSlotId = seedCounter(jdbc, date, 10, 0);
        UUID unrelatedSlotId = seedCounter(jdbc, date, 10, 1);

        UUID[] pair = seedGatedPair(orderSlotId, new BigDecimal("150.00"));
        UUID unrelatedLockId = ApprovalTestFixtures.seedActiveLock(jdbc, placerId, unrelatedSlotId, date);

        approvalService.approve(approverId, pair[1]);

        // The unrelated lock is not the approval's to touch.
        String unrelatedStatus = jdbc.queryForObject(
                "SELECT status FROM delivery_slot_locks WHERE id = ?", String.class, unrelatedLockId);
        assertThat(unrelatedStatus).as("unrelated lock must stay ACTIVE").isEqualTo("ACTIVE");
        assertThat(ApprovalTestFixtures.counterCount(jdbc, unrelatedSlotId, date))
                .as("unrelated slot capacity must not be freed").isEqualTo(1);

        // The resumed order holds a fresh lock on its own slot, and that slot's capacity
        // is taken exactly once.
        Integer freshLocks = jdbc.queryForObject(
                "SELECT count(*) FROM delivery_slot_locks WHERE user_id = ? AND slot_id = ? AND status = 'ACTIVE'",
                Integer.class, placerId, orderSlotId);
        assertThat(freshLocks).as("approval resume acquires its own lock").isEqualTo(1);
        assertThat(ApprovalTestFixtures.counterCount(jdbc, orderSlotId, date)).isEqualTo(1);

        String orderStatus = jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, pair[0]);
        assertThat(orderStatus).isIn("PAYMENT_PENDING", "CONFIRMED"); // dummy-gateway webhook tolerance
    }

    /** Gated order + PENDING request, same shape as ApprovalConcurrencyJpaIT. */
    private UUID[] seedGatedPair(UUID slotId, BigDecimal total) {
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId, ApprovalTestFixtures.seedAddress(jdbc, placerId), slotId, date, total,
                OrderStatus.PENDING_APPROVAL, null, Instant.now(), null, null, List.of(),
                companyId, null, null)));
        ApprovalRequest request = requestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), order.id(), companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.SITE_SUPERVISOR, null, Instant.now().plus(Duration.ofHours(24)),
                total, List.of(), null, List.of(), null,
                List.of(CompanyRole.SITE_SUPERVISOR, CompanyRole.OWNER), 24, 1, null, null));
        return new UUID[]{order.id(), request.id()};
    }
}
