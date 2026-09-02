package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.infra.persistence.entity.InvoiceEntity;
import com.builddash.backend.infra.persistence.repository.InvoiceJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceStalenessQueryBoundaryJpaIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceJpaRepository invoiceJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true) ON CONFLICT DO NOTHING", addressId, userId);
    }

    private UUID createOrderAndInvoice(String status, int attemptCount, Instant updatedAt) {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111104");
        UUID lockId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE') ON CONFLICT DO NOTHING", lockId, userId, slotId);
        jdbcTemplate.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, delivery_slot_lock_id, total_amount, status) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, 100, 'CONFIRMED')", orderId, userId, addressId, slotId, lockId);

        jdbcTemplate.update(
                "INSERT INTO invoices (id, order_id, status, attempt_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                invoiceId, orderId, status, attemptCount, Timestamp.from(updatedAt), Timestamp.from(updatedAt)
        );

        return invoiceId;
    }

    @Test
    void stalenessQueries_partitionCleanlyAtAttemptCountThree_withNoDoubleClaimAndNoDrop() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(15));
        Instant staleTime = cutoff.minus(Duration.ofMinutes(5));
        Instant recentTime = now.minus(Duration.ofMinutes(2));

        // Row A: Stale GENERATING with attemptCount = 2 (under boundary -> Scheduler)
        UUID rowA_staleAttempt2 = createOrderAndInvoice("GENERATING", 2, staleTime);

        // Row B: Stale GENERATING with attemptCount = 3 (EXACT BOUNDARY -> DLQ Worker)
        UUID rowB_staleAttempt3 = createOrderAndInvoice("GENERATING", 3, staleTime);

        // Row C: Stale GENERATING with attemptCount = 4 (above boundary -> DLQ Worker)
        UUID rowC_staleAttempt4 = createOrderAndInvoice("GENERATING", 4, staleTime);

        // Row D: Fresh PENDING with attemptCount = 0 (Scheduler)
        UUID rowD_pendingAttempt0 = createOrderAndInvoice("PENDING", 0, recentTime);

        // Row E: PENDING with attemptCount = 2 (Scheduler)
        UUID rowE_pendingAttempt2 = createOrderAndInvoice("PENDING", 2, recentTime);

        // Row F: DLQ_RETRY (DLQ Worker)
        UUID rowF_dlqRetry = createOrderAndInvoice("DLQ_RETRY", 3, recentTime);

        // Execute queries
        List<InvoiceEntity> schedulerResults = invoiceJpaRepository.findSchedulerClaimableInvoices(3, cutoff);
        List<InvoiceEntity> dlqResults = invoiceJpaRepository.findDlqClaimableInvoices(3, 6, cutoff);

        Set<UUID> schedulerIds = schedulerResults.stream().map(InvoiceEntity::getId).collect(Collectors.toSet());
        Set<UUID> dlqIds = dlqResults.stream().map(InvoiceEntity::getId).collect(Collectors.toSet());

        // 1. Scheduler partition assertions (< 3)
        assertThat(schedulerIds).contains(rowA_staleAttempt2, rowD_pendingAttempt0, rowE_pendingAttempt2);
        assertThat(schedulerIds).doesNotContain(rowB_staleAttempt3, rowC_staleAttempt4, rowF_dlqRetry);

        // 2. DLQ Worker partition assertions (>= 3 or DLQ_RETRY)
        assertThat(dlqIds).contains(rowB_staleAttempt3, rowC_staleAttempt4, rowF_dlqRetry);
        assertThat(dlqIds).doesNotContain(rowA_staleAttempt2, rowD_pendingAttempt0, rowE_pendingAttempt2);

        // 3. Exact boundary assertion for attemptCount == 3 (Row B): Must be picked up by DLQ worker and NOT scheduler
        assertThat(dlqIds.contains(rowB_staleAttempt3))
                .as("Exact boundary attemptCount == 3 must be claimed by DLQ worker")
                .isTrue();
        assertThat(schedulerIds.contains(rowB_staleAttempt3))
                .as("Exact boundary attemptCount == 3 must NOT be claimed by scheduler")
                .isFalse();

        // 4. No intersection (zero double-claim) between the test rows
        Set<UUID> testRowIds = Set.of(rowA_staleAttempt2, rowB_staleAttempt3, rowC_staleAttempt4, rowD_pendingAttempt0, rowE_pendingAttempt2, rowF_dlqRetry);
        Set<UUID> schedulerTestIds = schedulerIds.stream().filter(testRowIds::contains).collect(Collectors.toSet());
        Set<UUID> dlqTestIds = dlqIds.stream().filter(testRowIds::contains).collect(Collectors.toSet());

        assertThat(schedulerTestIds).doesNotContainAnyElementsOf(dlqTestIds);
        assertThat(schedulerTestIds.size() + dlqTestIds.size()).isEqualTo(testRowIds.size());
    }
}
