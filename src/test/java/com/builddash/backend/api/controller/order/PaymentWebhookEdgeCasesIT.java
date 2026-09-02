package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookEdgeCasesIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PaymentWebhookConfig webhookConfig;

    private UUID userId;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        User user = new User();
        String phone = "+9199" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
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
    void invalidSignature_returns401() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = String.format("""
                {
                    "orderId": "%s",
                    "status": "SUCCESS",
                    "signature": "bad-signature-1234567890abcdef"
                }
                """, orderId);

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingPaymentRow_refusesToConfirm_leavesPaymentPending_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(
                orderId, userId, addressId, UUID.fromString("11111111-1111-1111-1111-111111111101"),
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.PAYMENT_PENDING,
                UUID.randomUUID(), Instant.now(), null, null, List.of()
        ));

        String sig = sign(orderId, "SUCCESS");
        String payload = String.format("""
                {
                    "orderId": "%s",
                    "status": "SUCCESS",
                    "signature": "%s"
                }
                """, orderId, sig);

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void successOnCancelledOrder_recordsPaymentSuccessAndKeepsOrderCancelled() throws Exception {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(
                orderId, userId, addressId, UUID.fromString("11111111-1111-1111-1111-111111111101"),
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.CANCELLED,
                UUID.randomUUID(), Instant.now(), null, null, List.of()
        ));

        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new Payment(
                paymentId, orderId, "tx_cancelled_test", new BigDecimal("1000.00"),
                PaymentStatus.PENDING, null
        ));

        String sig = sign(orderId, "SUCCESS");
        String payload = String.format("""
                {
                    "orderId": "%s",
                    "status": "SUCCESS",
                    "signature": "%s"
                }
                """, orderId, sig);

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);

        Payment payment = paymentRepository.findLatestByOrderId(orderId).orElseThrow();
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void duplicateSuccess_isIdempotent() throws Exception {
        UUID orderId = UUID.randomUUID();
        orderRepository.save(new Order(
                orderId, userId, addressId, UUID.fromString("11111111-1111-1111-1111-111111111101"),
                LocalDate.now(), new BigDecimal("1000.00"), OrderStatus.CONFIRMED,
                UUID.randomUUID(), Instant.now(), null, null, List.of()
        ));

        String sig = sign(orderId, "SUCCESS");
        String payload = String.format("""
                {
                    "orderId": "%s",
                    "status": "SUCCESS",
                    "signature": "%s"
                }
                """, orderId, sig);

        mockMvc.perform(post("/api/webhooks/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
