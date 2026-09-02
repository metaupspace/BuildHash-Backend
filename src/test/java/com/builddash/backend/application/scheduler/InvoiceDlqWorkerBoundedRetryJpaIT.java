package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.InvoiceClaimService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H5.3 & H5.5 Real-PostgreSQL proof:
 * 1. Fresh GENERATING claims (<15m) are protected against duplicate claims.
 * 2. Invoice DLQ sweep bounds retries at 6 total attempts and transitions exhausted invoices to terminal FAILED.
 * 3. Terminal FAILED invoices are excluded from subsequent DLQ sweeps.
 */
class InvoiceDlqWorkerBoundedRetryJpaIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceClaimService invoiceClaimService;

    @Autowired
    private InvoiceDlqWorker invoiceDlqWorker;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID addressId;
    private UUID slotId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', true, now(), now())",
                addressId, userId);

        slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);

        orderId = UUID.randomUUID();
        orderRepository.save(new Order(orderId, userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("500.00"), OrderStatus.CONFIRMED, null, Instant.now(), null, null, List.of()));
    }

    @Test
    void freshGeneratingClaim_isNotDuplicated() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, orderId, null, InvoiceStatus.PENDING, null, "application/pdf", null, 0, Instant.now(), Instant.now());
        invoiceRepository.save(invoice);

        // 1. First claim: transitions PENDING -> GENERATING, attemptCount = 1
        Invoice firstClaim = invoiceClaimService.claim(invoiceId);
        assertThat(firstClaim.status()).isEqualTo(InvoiceStatus.GENERATING);
        assertThat(firstClaim.attemptCount()).isEqualTo(1);

        // 2. Second claim immediately (fresh < 15m): returns existing claim without bumping attemptCount
        Invoice secondClaim = invoiceClaimService.claim(invoiceId);
        assertThat(secondClaim.status()).isEqualTo(InvoiceStatus.GENERATING);
        assertThat(secondClaim.attemptCount()).isEqualTo(1);
    }

    @Test
    void invoiceDlq_exhaustsRetriesAndTransitionsToFailed() {
        UUID invoiceId = UUID.randomUUID();
        // Seed invoice at attempt 6 in DLQ_RETRY status (updated 1 hour ago)
        jdbcTemplate.update("INSERT INTO invoices (id, order_id, status, attempt_count, created_at, updated_at) " +
                "VALUES (?, ?, 'DLQ_RETRY', 6, now() - interval '2 hours', now() - interval '1 hour')",
                invoiceId, orderId);

        // Run DLQ sweep (attempt 6)
        invoiceDlqWorker.runTwoHourDlqSweep();

        // Invoice reached attemptCount 6 and is now terminal FAILED
        Invoice updated = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(updated.status()).isEqualTo(InvoiceStatus.FAILED);
        assertThat(updated.attemptCount()).isEqualTo(6);

        // Subsequent DLQ sweeps will not pick up the FAILED invoice
        List<Invoice> claimable = invoiceRepository.findDlqClaimableInvoices(3, 6, Instant.now().minusSeconds(900));
        assertThat(claimable.stream().noneMatch(i -> i.id().equals(invoiceId))).isTrue();
    }
}
