package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvoiceControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String guestToken;
    private UUID userId;
    private UUID addressId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING", userId, "+919" + String.format("%07d", Math.abs(userId.getLeastSignificantBits() % 10000000L)));
        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, deviceId, List.of("USER")).token();

        UUID guestId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now()) ON CONFLICT DO NOTHING", guestId);
        guestToken = "Bearer " + tokenIssuer.issueGuestToken(guestId).token();

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())", addressId, userId);
    }

    private Order createOrder(UUID ownerId, OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111102");
        UUID lockId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE') ON CONFLICT DO NOTHING", lockId, ownerId, slotId);

        Order order = new Order(
                orderId,
                ownerId,
                addressId,
                slotId,
                LocalDate.now(),
                new BigDecimal("500.00"),
                status,
                lockId,
                Instant.now(),
                "driver-1",
                "+919876543210",
                List.of()
        );

        return orderRepository.save(order);
    }

    @Test
    void getInvoice_readyStatus_returnsSignedUrlAndInvoiceNumber() throws Exception {
        Order order = createOrder(userId, OrderStatus.CONFIRMED);
        UUID invoiceId = UUID.randomUUID();
        String storageKey = "invoices/" + order.id() + "/" + invoiceId + ".pdf";
        String invoiceNum = "INV-2627-" + String.format("%06d", (int)(Math.random() * 900000 + 100000));

        jdbcTemplate.update(
                "INSERT INTO invoices (id, order_id, number, status, storage_key, content_type, attempt_count, generated_at, created_at, updated_at) VALUES (?, ?, ?, 'READY', ?, 'application/pdf', 1, now(), now(), now())",
                invoiceId, order.id(), invoiceNum, storageKey
        );

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.invoiceNumber").value(invoiceNum))
                .andExpect(jsonPath("$.url").isString())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void getInvoice_generatingStatus_returnsPlaceholderWithNullUrlAndNumber() throws Exception {
        Order order = createOrder(userId, OrderStatus.CONFIRMED);
        UUID invoiceId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO invoices (id, order_id, number, status, storage_key, content_type, attempt_count, created_at, updated_at) VALUES (?, ?, null, 'GENERATING', null, 'application/pdf', 1, now(), now())",
                invoiceId, order.id()
        );

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.invoiceNumber").isEmpty())
                .andExpect(jsonPath("$.url").isEmpty())
                .andExpect(jsonPath("$.expiresAt").isEmpty());
    }

    @Test
    void getInvoice_whenNoInvoiceRowYet_returnsGeneratingPlaceholder() throws Exception {
        Order order = createOrder(userId, OrderStatus.CONFIRMED);

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATING"))
                .andExpect(jsonPath("$.invoiceNumber").isEmpty())
                .andExpect(jsonPath("$.url").isEmpty())
                .andExpect(jsonPath("$.expiresAt").isEmpty());
    }

    @Test
    void getInvoice_whenNotOwnerOnReady_returnsNotFound404() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, '+919999999993', now(), now()) ON CONFLICT DO NOTHING", otherUserId);
        Order order = createOrder(otherUserId, OrderStatus.CONFIRMED);

        UUID invoiceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, order_id, number, status, storage_key, content_type, attempt_count, generated_at, created_at, updated_at) VALUES (?, ?, 'INV-2627-000042', 'READY', 'invoices/key.pdf', 'application/pdf', 1, now(), now(), now())",
                invoiceId, order.id()
        );

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void getInvoice_whenNotOwnerOnGenerating_returnsNotFound404() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, '+919999999994', now(), now()) ON CONFLICT DO NOTHING", otherUserId);
        Order order = createOrder(otherUserId, OrderStatus.CONFIRMED);

        UUID invoiceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO invoices (id, order_id, number, status, storage_key, content_type, attempt_count, created_at, updated_at) VALUES (?, ?, null, 'GENERATING', null, 'application/pdf', 1, now(), now())",
                invoiceId, order.id()
        );

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void getInvoice_byGuest_returnsForbidden403() throws Exception {
        Order order = createOrder(userId, OrderStatus.CONFIRMED);

        mockMvc.perform(get("/orders/{id}/invoice", order.id())
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());
    }
}
