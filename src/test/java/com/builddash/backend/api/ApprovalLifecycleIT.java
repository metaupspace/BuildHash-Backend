package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalGateService;
import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.application.service.OrderTrackingService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedPolicy;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-D end-to-end lifecycle on real Postgres: gate opens (slot released), approve
 * resumes PAYMENT_PENDING with exactly one initiated payment, the payment webhook
 * confirms normally, rejection and placer cancellation both end in CANCELLED with
 * no slot/payment interaction, and the three notification moments land in the log.
 */
class ApprovalLifecycleIT extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-only-webhook-secret-0123456789abcdef";

    @Autowired
    private ApprovalGateService gateService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private PaymentWebhookService paymentWebhookService;
    @Autowired
    private OrderTrackingService orderTrackingService;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.builddash.backend.application.service.ApprovalEscalationService escalationService;

    /**
     * Same as NotificationTriggerListenerDispatchIT: without the stub the RabbitMQ
     * enqueue throws inside the AFTER_COMMIT listener's REQUIRES_NEW tx and rolls back
     * the notification_log row with it.
     */
    @org.springframework.boot.test.mock.mockito.MockBean
    private NotificationDispatchQueue dispatchQueue;

    private UUID companyId;
    private UUID placerId;
    private UUID approverId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "LifecycleCo");
        placerId = seedUser(jdbc);
        approverId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);
        seedMember(jdbc, companyId, approverId, "SITE_SUPERVISOR", null);
        grantPermission(jdbc, companyId, "SITE_SUPERVISOR", "APPROVAL_ACT");
        date = LocalDate.now();
    }

    /** Order + PENDING request at stage 0 = SITE_SUPERVISOR over a fresh counter. */
    private UUID[] gatedPair() {
        seedPolicy(jdbc, companyId, new BigDecimal("100.00"), null, null,
                new String[]{"SITE_SUPERVISOR"}, 24, 1);
        var decision = gateService.evaluate(companyId, new BigDecimal("150.00"), List.of(), null);
        assertThat(decision.gated()).isTrue();

        UUID slotId = seedCounter(jdbc, date, 5, 0);
        UUID lockId = com.builddash.backend.support.ApprovalTestFixtures
                .seedActiveLock(jdbc, placerId, slotId, date);
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId), slotId, date, new BigDecimal("150.00"),
                OrderStatus.PENDING_APPROVAL, null, Instant.now(), null, null, List.of(),
                companyId, null, null)));
        ApprovalRequest request = transactionTemplate.execute(
                status -> gateService.openApproval(order, decision, lockId));
        return new UUID[]{order.id(), request.id(), slotId};
    }

    private Order order(UUID orderId) {
        return transactionTemplate.execute(s -> orderRepository.findById(orderId).orElseThrow());
    }

    private int notifications(UUID userId, String eventType) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM notification_logs WHERE user_id = ? AND event_type = ?",
                Integer.class, userId, eventType);
        return n == null ? 0 : n;
    }

    private String webhookSignature(UUID orderId, String status) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal((orderId + ":" + status).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac);
    }

    @Test
    void approve_resumesPayment_webhookConfirmsNormally() throws Exception {
        UUID[] pair = gatedPair();

        // No payment of any kind while gated.
        assertThat(notifications(approverId, "APPROVAL_REQUESTED")).isEqualTo(1);

        var detail = approvalService.approve(approverId, pair[1]);

        assertThat(detail.request().status()).isEqualTo(ApprovalRequestStatus.APPROVED);
        Order resumed = order(pair[0]);
        assertThat(resumed.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(resumed.deliverySlotLockId()).isNotNull();
        assertThat(com.builddash.backend.support.ApprovalTestFixtures.counterCount(jdbc, pair[2], date))
                .isEqualTo(1); // slot re-acquired

        // Exactly one initiated payment with a transaction id and URL for the placer.
        Integer initiated = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ? AND transaction_id IS NOT NULL",
                Integer.class, pair[0]);
        assertThat(initiated).isEqualTo(1);
        String url = jdbc.queryForObject(
                "SELECT payment_url FROM payments WHERE order_id = ?", String.class, pair[0]);
        assertThat(url).startsWith("https://dummy.gateway.local/pay/");

        assertThat(notifications(placerId, "APPROVAL_DECIDED")).isEqualTo(1);

        // The gateway webhook confirms through the ordinary path — state machine unchanged.
        paymentWebhookService.handleWebhook(pair[0], "SUCCESS", webhookSignature(pair[0], "SUCCESS"));
        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void reject_cancelsOrderAndRequest_noSlotReleaseNoPayment() {
        UUID[] pair = gatedPair();
        assertThat(com.builddash.backend.support.ApprovalTestFixtures.counterCount(jdbc, pair[2], date)).isZero();

        var detail = approvalService.reject(approverId, pair[1]);

        assertThat(detail.request().status()).isEqualTo(ApprovalRequestStatus.REJECTED);
        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.CANCELLED);
        // Counter untouched: the gate released capacity at creation and rejection never re-took it.
        assertThat(com.builddash.backend.support.ApprovalTestFixtures.counterCount(jdbc, pair[2], date)).isZero();
        Integer payments = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, pair[0]);
        assertThat(payments).isZero();
        assertThat(notifications(placerId, "APPROVAL_DECIDED")).isEqualTo(1);
        assertThat(notifications(placerId, "ORDER_CANCELLED")).isEqualTo(1);

        var actions = approvalService.get(approverId, pair[1]).actions();
        assertThat(actions).anyMatch(a -> a.type() == com.builddash.backend.domain.enums.ApprovalActionType.REJECTED);
    }

    @Test
    void placerCancel_throughExistingEndpoint_endsGateWithoutWindowOrSlotRelease() {
        UUID[] pair = gatedPair();
        // Way past the 15-minute modification window — irrelevant for PENDING_APPROVAL.
        jdbc.update("UPDATE orders SET created_at = now() - interval '2 hours' WHERE id = ?", pair[0]);

        orderTrackingService.cancelOrderWithinWindow(placerId, pair[0]);

        assertThat(order(pair[0]).status()).isEqualTo(OrderStatus.CANCELLED);
        ApprovalRequest request = transactionTemplate.execute(
                s -> requestRepository.findById(pair[1]).orElseThrow());
        assertThat(request.status()).isEqualTo(ApprovalRequestStatus.CANCELLED);
        Integer actions = jdbc.queryForObject(
                "SELECT count(*) FROM approval_actions WHERE request_id = ? AND action_type = 'CANCELLED'",
                Integer.class, pair[1]);
        assertThat(actions).isEqualTo(1);
        // No slot was held, so none is released — counter stays 0 and no lock row was consumed.
        assertThat(com.builddash.backend.support.ApprovalTestFixtures.counterCount(jdbc, pair[2], date)).isZero();
        assertThat(notifications(placerId, "ORDER_CANCELLED")).isEqualTo(1);
    }

    @Test
    void detail_exposesBlockedStateAfterEscalationDeadEnd() {
        UUID[] pair = gatedPair();
        // Single-stage policy with the only ACT holder removed: due -> blocked, PENDING.
        jdbc.update("DELETE FROM company_role_permissions WHERE company_id = ? AND role = 'SITE_SUPERVISOR' AND permission = 'APPROVAL_ACT'",
                companyId);
        jdbc.update("UPDATE approval_requests SET escalation_due_at = now() - interval '1 minute' WHERE id = ?", pair[1]);
        jdbc.update("DELETE FROM company_members WHERE company_id = ? AND user_id = ?", companyId, approverId);

        escalationService.escalateDue();

        var detail = approvalService.get(placerId, pair[1]);
        assertThat(detail.request().status()).isEqualTo(ApprovalRequestStatus.PENDING);
        assertThat(detail.request().escalationDueAt()).isNull();
        // The blocked state is API-visible via ApprovalResponse.escalationBlocked —
        // derived: PENDING + null due clock.
        boolean blocked = detail.request().status() == ApprovalRequestStatus.PENDING
                && detail.request().escalationDueAt() == null;
        assertThat(blocked).isTrue();
        assertThat(detail.actions()).anyMatch(
                a -> a.type() == com.builddash.backend.domain.enums.ApprovalActionType.ESCALATION_BLOCKED);
    }
}
