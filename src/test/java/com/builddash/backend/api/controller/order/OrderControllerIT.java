package com.builddash.backend.api.controller.order;

import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    private String validToken;
    @Autowired private com.builddash.backend.domain.port.UserRepository userRepository;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        String phone = "+911111900001";
        com.fasterxml.jackson.databind.JsonNode tokens = loginViaOtp(phone, "Test-Device");
        validToken = "Bearer " + tokens.get("accessToken").asText();
        userId = userRepository.findByPhone(phone).orElseThrow().getId();
    }



    
    @Test
    void getOrder_happyPath_returnsOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Line 1', 'City', 'MH', '400000', now(), now())", addressId, userId);
        Order order = new Order(orderId, userId, addressId, UUID.fromString("33333333-3333-3333-3333-333333333333"), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of());
        orderRepository.save(order);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }


    
    @Test
    void getOrder_whenNotOwner_returnsNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING", otherUserId, "+919999999998");
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Line 1', 'City', 'MH', '400000', now(), now())", addressId, otherUserId);
        Order order = new Order(orderId, otherUserId, addressId, UUID.fromString("33333333-3333-3333-3333-333333333333"), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of());
        orderRepository.save(order);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorder_happyPath_createsNewCart() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Line 1', 'City', 'MH', '400000', now(), now())", addressId, userId);

        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Cat', 'cat-slug')", categoryId);

        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Product A', 'slug', ?, 'ACTIVE', '1234', now(), now())", productId, categoryId);
        jdbcTemplate.update("INSERT INTO product_base_prices (product_id, price, created_at, updated_at) VALUES (?, 60.00, now(), now())", productId);
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('1234', 'Desc', 18.00, 'Cat', now(), now()) ON CONFLICT DO NOTHING");

        Order order = new Order(orderId, userId, addressId, UUID.fromString("33333333-3333-3333-3333-333333333333"), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.DELIVERED, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of(
            new com.builddash.backend.domain.model.OrderLineItem(UUID.randomUUID(), productId, 2, new BigDecimal("50.00"), BigDecimal.ZERO)
        ));
        orderRepository.save(order);

        // Before reorder, verify base price is now 60.00. 2 * 60 = 120.00 vs expected total 100.00.
        // Tax is 18% on 120.00 = 21.60.
        // Final total should be 120.00 + 21.60 = 141.60.
        mockMvc.perform(post("/orders/{id}/reorder", orderId)
                .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    
    @Test
    void retryPayment_happyPath_returnsPendingOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Line 1', 'City', 'MH', '400000', now(), now())", addressId, userId);


        Order order = new Order(
                orderId,
                userId,
                addressId,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                LocalDate.now(),
                new BigDecimal("100.00"),
                OrderStatus.PAYMENT_PENDING,
                UUID.randomUUID(),
                java.time.Instant.now(),
                null,
                null,
                java.util.List.of()
        );
        orderRepository.save(order);

        Payment payment = new Payment(
                UUID.randomUUID(),
                orderId,
                null,
                new BigDecimal("100.00"),
                PaymentStatus.FAILED,
                null
        );
        paymentRepository.save(payment);

        mockMvc.perform(post("/orders/{id}/payments/retry", orderId)
                .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }
}
