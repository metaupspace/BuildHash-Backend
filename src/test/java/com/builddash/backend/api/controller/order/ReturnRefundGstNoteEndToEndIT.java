package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateReturnRequest;
import com.builddash.backend.api.dto.request.RefundWebhookRequest;
import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.domain.enums.GstNoteType;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.GstNote;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReturnRefundGstNoteEndToEndIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private GstNoteRepository gstNoteRepository;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private PaymentWebhookConfig webhookConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String adminToken;
    private UUID userId;
    private UUID addressId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        String phone = "+911111900004";
        var tokens = loginViaOtp(phone, "Test-Device");
        userToken = "Bearer " + tokens.get("accessToken").asText();
        userId = userRepository.findByPhone(phone).orElseThrow().getId();

        adminToken = "Bearer " + tokenIssuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("ADMIN")).token();

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())", addressId, userId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Electrical', 'electrical-slug', 7) ON CONFLICT (id) DO UPDATE SET return_window_days = 7", categoryId);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Copper Wire', 'copper-wire', ?, 'ACTIVE', '8544', now(), now()) ON CONFLICT (id) DO NOTHING", productId, categoryId);
        jdbcTemplate.update("INSERT INTO product_base_prices (product_id, price, created_at, updated_at) VALUES (?, 100.00, now(), now()) ON CONFLICT (product_id) DO NOTHING", productId);
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('8544', 'Wire', 18.00, 'Electrical', now(), now()) ON CONFLICT (hsn_code) DO NOTHING");
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fullReturnRefundGstCreditNoteChain_succeedsEndToEnd() throws Exception {
        // 1. Deliver order
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        UUID lockId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE') ON CONFLICT DO NOTHING", lockId, userId, slotId);

        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                productId,
                2,
                new BigDecimal("100.00"),
                new BigDecimal("36.00"),
                new BigDecimal("236.00")
        );

        Order order = new Order(
                orderId,
                userId,
                addressId,
                slotId,
                LocalDate.now(),
                new BigDecimal("236.00"),
                OrderStatus.DELIVERED,
                lockId,
                Instant.now(),
                "driver-1",
                "+919876543210",
                List.of(item)
        );
        orderRepository.save(order);

        // Record successful payment for the order
        jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, transaction_id, amount, status, payment_url, created_at) VALUES (?, ?, 'tx_e2e_wire', 236.00, 'SUCCESS', 'http://pay', now())",
                UUID.randomUUID(), orderId
        );

        // 2. Customer submits Return request with 2 photos
        CreateReturnRequest returnRequest = new CreateReturnRequest(
                ReturnReason.DEFECTIVE,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(returnRequest)
        );

        MockMultipartFile photoPart1 = new MockMultipartFile(
                "photos",
                "defect1.jpg",
                "image/jpeg",
                new byte[]{10, 20, 30}
        );

        String createRes = mockMvc.perform(multipart("/orders/{id}/return", orderId)
                        .file(jsonPart)
                        .file(photoPart1)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.lineItems[0].quantityRequested").value(1))
                .andExpect(jsonPath("$.lineItems[0].refundAmount").value(118.00))
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(createRes).get("id").asText());

        // 3. Warehouse advances return state to PICKED_UP via HTTP endpoints and performs QC Pass
        mockMvc.perform(post("/returns/{id}/approve", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKUP_SCHEDULED"));

        mockMvc.perform(post("/returns/{id}/pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_INITIATED"))
                .andExpect(jsonPath("$.refund.status").value("PENDING"))
                .andExpect(jsonPath("$.refund.amount").value(118.00));

        var refundOpt = refundRepository.findByReturnId(returnId);
        assertThat(refundOpt).isPresent();
        var refund = refundOpt.get();
        String gatewayRefundId = refund.gatewayRefundId();
        assertThat(gatewayRefundId).isNotNull();

        // 4. Gateway posts successful refund webhook
        String webhookStatus = "SUCCESS";
        String payload = returnId + ":" + gatewayRefundId + ":" + webhookStatus;
        String signature = computeHmac(payload, webhookConfig.getWebhookSecret());

        RefundWebhookRequest webhookReq = new RefundWebhookRequest(
                returnId,
                gatewayRefundId,
                webhookStatus,
                signature
        );

        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookReq)))
                .andExpect(status().isOk());

        // 5. Assert Return is REFUND_COMPLETED, Refund is SUCCESS, and CREDIT_NOTE GstNote is persisted
        var finalReturn = returnRepository.findById(returnId).orElseThrow();
        assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REFUND_COMPLETED);

        var finalRefund = refundRepository.findByReturnId(returnId).orElseThrow();
        assertThat(finalRefund.status()).isEqualTo(RefundStatus.SUCCESS);

        List<GstNote> notes = gstNoteRepository.findAllByReturnId(returnId);
        assertThat(notes).hasSize(1);

        GstNote creditNote = notes.get(0);
        assertThat(creditNote.noteType()).isEqualTo(GstNoteType.CREDIT);
        assertThat(creditNote.amount()).isEqualByComparingTo("118.00");
        assertThat(creditNote.number()).matches("^CRN-\\d{4}-\\d{6}$");
    }
}
