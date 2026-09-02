package com.builddash.backend.application.impl;

import com.builddash.backend.application.scheduler.DeliverySlotGenerator;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentReconciliationService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationType;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.PaymentReconciliation;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.domain.port.PaymentReconciliationRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentReconciliationService reconciliationService;

    @Autowired
    private StaleOrderSweepService sweepService;

    @Autowired
    private PaymentWebhookService webhookService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentReconciliationRepository reconciliationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private DeliverySlotService deliverySlotService;

    @Autowired
    private DeliverySlotGenerator deliverySlotGenerator;

    @Autowired
    private PaymentWebhookConfig webhookConfig;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID addressId;
    private final UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");

    @BeforeEach
    void setUp() {
        deliverySlotGenerator.generateSlotsForRange(LocalDate.now(), LocalDate.now().plusDays(1));

        User user = new User();
        String phone = "+9198" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
        user.setPhone(phone);
        userId = userRepository.save(user).getId();

        addressId = addressRepository.save(new Address(
                UUID.randomUUID(), userId, "HOME", "123 Street", null, "City", "State", "400001", 12.34, 56.78, true
        )).id();
    }

    private String sign(UUID orderId, String status) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((orderId + ":" + status).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void stalePendingPayment_reconciledToConfirmedWhenGatewaySuccess() {
        UUID orderId = UUID.randomUUID();
        DeliverySlotLock lock = deliverySlotService.acquireLock(userId, slotId, LocalDate.now(), Duration.ofMinutes(15));
        jdbcTemplate.update("UPDATE delivery_slot_locks SET expires_at = now() - interval '1 minute' WHERE id = ?", lock.id());

        orderRepository.save(new Order(
                orderId, userId, addressId, slotId,
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.PAYMENT_PENDING,
                lock.id(), Instant.now().minus(Duration.ofMinutes(20)), null, null, List.of()
        ));

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new Payment(
                paymentId, orderId, "dummy_tx_success_" + UUID.randomUUID(), new BigDecimal("1000.00"),
                PaymentStatus.PENDING, "https://dummy.gateway.local/pay"
        ));

        // When sweep runs, reconciliation checks gateway and finds SUCCESS
        sweepService.sweepStaleOrders();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);

        Payment payment = paymentRepository.findLatestByOrderId(orderId).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);

        assertThat(invoiceRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void stalePendingPayment_cancelledWhenGatewayFailed() {
        UUID orderId = UUID.randomUUID();
        DeliverySlotLock lock = deliverySlotService.acquireLock(userId, slotId, LocalDate.now(), Duration.ofMinutes(15));
        jdbcTemplate.update("UPDATE delivery_slot_locks SET expires_at = now() - interval '1 minute' WHERE id = ?", lock.id());

        orderRepository.save(new Order(
                orderId, userId, addressId, slotId,
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.PAYMENT_PENDING,
                lock.id(), Instant.now().minus(Duration.ofMinutes(20)), null, null, List.of()
        ));

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new Payment(
                paymentId, orderId, "dummy_tx_fail_" + UUID.randomUUID(), new BigDecimal("1000.00"),
                PaymentStatus.PENDING, "https://dummy.gateway.local/pay"
        ));

        sweepService.sweepStaleOrders();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);

        Payment payment = paymentRepository.findLatestByOrderId(orderId).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void webhookSuccessOnCancelledOrder_recordsDurableReconciliationWorkItem() {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(
                orderId, userId, addressId, slotId,
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.CANCELLED,
                UUID.randomUUID(), Instant.now(), null, null, List.of()
        ));

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new Payment(
                paymentId, orderId, "tx_capture_cancelled", new BigDecimal("1000.00"),
                PaymentStatus.PENDING, null
        ));

        String sig = sign(orderId, "SUCCESS");
        webhookService.handleWebhook(orderId, "SUCCESS", sig);

        // Order remains cancelled
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);

        // Payment marked SUCCESS
        Payment payment = paymentRepository.findLatestByOrderId(orderId).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);

        // Durable reconciliation work item exists in database
        Optional<PaymentReconciliation> recOpt = reconciliationRepository.findByOrderIdAndType(
                orderId, PaymentReconciliationType.CAPTURED_ON_CANCELLED_ORDER);
        assertThat(recOpt).isPresent();
        assertThat(recOpt.get().status()).isEqualTo(PaymentReconciliationStatus.FLAGGED_MANUAL);
        assertThat(recOpt.get().amount()).isEqualByComparingTo("1000.00");

        // Duplicate webhook is idempotent
        webhookService.handleWebhook(orderId, "SUCCESS", sig);
        List<PaymentReconciliation> recs = reconciliationRepository.findByStatus(PaymentReconciliationStatus.FLAGGED_MANUAL);
        long countForOrder = recs.stream().filter(r -> r.orderId().equals(orderId)).count();
        assertThat(countForOrder).isEqualTo(1);
    }
}
