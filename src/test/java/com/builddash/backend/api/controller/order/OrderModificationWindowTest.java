package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderModificationWindowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.builddash.backend.domain.port.UserRepository userRepository;

    private String validToken;
    private UUID userId;
    private UUID addressId;
    private UUID slotId1;
    private UUID slotId2;

    @BeforeEach
    void setUp() throws Exception {
        String phone = "+91987" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10000000L));
        com.fasterxml.jackson.databind.JsonNode tokens = loginViaOtp(phone, "Test-Device-Window");
        validToken = "Bearer " + tokens.get("accessToken").asText();
        userId = userRepository.findByPhone(phone).orElseThrow().getId();

        addressId = UUID.randomUUID();
        slotId1 = UUID.fromString("11111111-1111-1111-1111-111111111103");
        slotId2 = UUID.fromString("11111111-1111-1111-1111-111111111104");

        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true) ON CONFLICT DO NOTHING", addressId, userId);

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, ?, 50, 1) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 1", slotId1, today);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, ?, 50, 0) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 0", slotId2, today);
    }

    private Order createOrderWithPlacedAt(Instant placedAt) {
        UUID orderId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'CONSUMED') ON CONFLICT DO NOTHING", lockId, userId, slotId1, today);

        Order order = new Order(
                orderId,
                userId,
                addressId,
                slotId1,
                today,
                new BigDecimal("299.00"),
                OrderStatus.CONFIRMED,
                lockId,
                placedAt,
                null,
                null,
                List.of()
        );
        return orderRepository.save(order);
    }

    @Test
    void reschedule_withinModificationWindow_succeeds() throws Exception {
        // Placed 5 minutes ago (within 15-minute window)
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(5)));

        String payload = objectMapper.writeValueAsString(Map.of(
                "newSlotId", slotId2,
                "slotDate", LocalDate.now().toString()
        ));

        mockMvc.perform(post("/orders/{id}/reschedule", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void reschedule_outsideModificationWindow_returns409Conflict() throws Exception {
        // Placed 16 minutes ago (past 15-minute window)
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(16)));

        String payload = objectMapper.writeValueAsString(Map.of(
                "newSlotId", slotId2,
                "slotDate", LocalDate.now().toString()
        ));

        mockMvc.perform(post("/orders/{id}/reschedule", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODIFICATION_WINDOW_EXPIRED"));
    }

    @Test
    void cancel_withinModificationWindow_succeeds() throws Exception {
        // Placed 2 minutes ago
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(2)));

        mockMvc.perform(post("/orders/{id}/cancel", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_outsideModificationWindow_returns409Conflict() throws Exception {
        // Placed 20 minutes ago
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(20)));

        mockMvc.perform(post("/orders/{id}/cancel", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODIFICATION_WINDOW_EXPIRED"));
    }

    @Test
    void cancel_whenOrderStatusFlippedConcurrently_throws409Conflict() throws Exception {
        // Order was placed within window, but status was transitioned to PACKED / DISPATCHED concurrently
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(2)));
        Order flipped = order.pack().dispatch("d-1", "+919876543210");
        orderRepository.save(flipped);

        mockMvc.perform(post("/orders/{id}/cancel", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    void reschedule_whenOrderStatusFlippedConcurrently_throws409Conflict() throws Exception {
        // Order was placed within window, but status was transitioned to PACKED / DISPATCHED concurrently
        Order order = createOrderWithPlacedAt(Instant.now().minus(Duration.ofMinutes(2)));
        Order flipped = order.pack().dispatch("d-1", "+919876543210");
        orderRepository.save(flipped);

        String payload = objectMapper.writeValueAsString(Map.of(
                "newSlotId", slotId2,
                "slotDate", LocalDate.now().toString()
        ));

        mockMvc.perform(post("/orders/{id}/reschedule", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }
}
