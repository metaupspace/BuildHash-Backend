package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.OrderRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConfirmedInvoiceAtomicityJpaIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentWebhookService webhookService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PaymentWebhookConfig webhookConfig;

    @Autowired
    private DeliverySlotService deliverySlotService;

    @Autowired
    private com.builddash.backend.application.scheduler.DeliverySlotGenerator deliverySlotGenerator;

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
    void paymentConfirmation_atomicallyInitializesPendingInvoice() {
        UUID orderId = UUID.randomUUID();
        com.builddash.backend.domain.model.DeliverySlotLock lock =
                deliverySlotService.acquireLock(userId, slotId, LocalDate.now(), java.time.Duration.ofMinutes(15));

        orderRepository.save(new Order(
                orderId, userId, addressId, slotId,
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.PAYMENT_PENDING,
                lock.id(), Instant.now(), null, null, List.of()
        ));

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new Payment(
                paymentId, orderId, "tx_invoice_test", new BigDecimal("1000.00"),
                PaymentStatus.PENDING, null
        ));

        // Prior to webhook, no invoice exists
        assertThat(invoiceRepository.findByOrderId(orderId)).isEmpty();

        String sig = sign(orderId, "SUCCESS");
        webhookService.handleWebhook(orderId, "SUCCESS", sig);

        // Verify order is CONFIRMED
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify invoice row was atomically initialized in PENDING status
        Optional<Invoice> invoiceOpt = invoiceRepository.findByOrderId(orderId);
        assertThat(invoiceOpt).isPresent();
        assertThat(invoiceOpt.get().status()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(invoiceOpt.get().orderId()).isEqualTo(orderId);
    }
}
