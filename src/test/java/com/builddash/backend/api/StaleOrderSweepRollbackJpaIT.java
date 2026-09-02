package com.builddash.backend.api;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * H2.3 on real Postgres: the sweep transaction must survive its own best-effort slot
 * release. Before the fix, releaseLock joined the sweep's REQUIRES_NEW transaction, so
 * a release failure marked that transaction rollback-only — the catch swallowed the
 * exception but the commit then threw UnexpectedRollbackException and the CANCELLED
 * state was lost. The release failure is injected through a SpyBean (the lock row must
 * stay alive for the stale selector to pick the order up at all), which is exactly the
 * real-world shape: a transient release failure under an expired lock.
 */
class StaleOrderSweepRollbackJpaIT extends AbstractIntegrationTest {

    @Autowired
    private StaleOrderSweepService sweepService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @SpyBean
    private DeliverySlotService deliverySlotService;

    private UUID seedStalePaymentPendingOrder(UUID userId, UUID lockId, UUID slotId) {
        UUID addressId = ApprovalTestFixtures.seedAddress(jdbc, userId);
        LocalDate date = LocalDate.now().plusDays(1);
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), userId, addressId, slotId, date, new BigDecimal("150.00"),
                OrderStatus.PAYMENT_PENDING, lockId, Instant.now(), null, null, List.of(),
                null, null, null)));
        return order.id();
    }

    @Test
    void releaseFailure_cannotRollBackCancellation() {
        UUID userId = seedUser(jdbc);
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotId = seedCounter(jdbc, date, 10, 1);
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, userId, slotId, date);
        // The stale selector requires an expired lock; the release underneath it fails.
        jdbc.update("UPDATE delivery_slot_locks SET expires_at = now() - interval '5 minutes' WHERE id = ?", lockId);
        UUID orderId = seedStalePaymentPendingOrder(userId, lockId, slotId);
        doThrow(new RuntimeException("simulated release failure"))
                .when(deliverySlotService).releaseLock(any(UUID.class), any(UUID.class));

        sweepService.sweepStaleOrders();

        String status = jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(status)
                .as("stale cancellation must stay CANCELLED even when slot release fails")
                .isEqualTo("CANCELLED");
    }

    @Test
    void releaseSuccess_cancelsOrderReleasesLockReturnsCapacity() {
        UUID userId = seedUser(jdbc);
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotId = seedCounter(jdbc, date, 10, 1);
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, userId, slotId, date);
        jdbc.update("UPDATE delivery_slot_locks SET expires_at = now() - interval '5 minutes' WHERE id = ?", lockId);
        UUID orderId = seedStalePaymentPendingOrder(userId, lockId, slotId);

        sweepService.sweepStaleOrders();

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM delivery_slot_locks WHERE id = ?", String.class, lockId))
                .isEqualTo("RELEASED");
        assertThat(ApprovalTestFixtures.counterCount(jdbc, slotId, date))
                .as("released capacity returns to the counter exactly once")
                .isZero();
    }
}
