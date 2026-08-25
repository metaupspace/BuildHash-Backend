package com.builddash.backend.api.controller.order;

import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderTrackingControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryTrackingEventRepository trackingEventRepository;

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
        com.fasterxml.jackson.databind.JsonNode tokens = loginViaOtp(phone, "Test-Device-Tracking");
        validToken = "Bearer " + tokens.get("accessToken").asText();
        userId = userRepository.findByPhone(phone).orElseThrow().getId();

        addressId = UUID.randomUUID();
        slotId1 = UUID.fromString("11111111-1111-1111-1111-111111111101");
        slotId2 = UUID.fromString("11111111-1111-1111-1111-111111111102");

        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true) ON CONFLICT DO NOTHING", addressId, userId);

        LocalDate today = LocalDate.now();
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, ?, 50, 1) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 1", slotId1, today);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, ?, 50, 0) ON CONFLICT (slot_id, slot_date) DO UPDATE SET current_count = 0", slotId2, today);
    }

    private Order createSavedOrder(UUID ownerId, OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'CONSUMED') ON CONFLICT DO NOTHING", lockId, ownerId, slotId1, today);

        Order order = new Order(
                orderId,
                ownerId,
                addressId,
                slotId1,
                today,
                new BigDecimal("299.00"),
                status,
                lockId,
                Instant.now(),
                null,
                null,
                List.of()
        );
        return orderRepository.save(order);
    }

    @Test
    void getTracking_happyPath_returnsTrackingDetails() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.DISPATCHED);
        order = order.updateDriver("driver-42", "+919988776655");
        orderRepository.save(order);

        trackingEventRepository.save(new DeliveryTrackingEvent(
                UUID.randomUUID(), order.id(), OrderStatus.DISPATCHED, 12.9716, 77.5946, Instant.now()
        ));

        mockMvc.perform(get("/orders/{id}/tracking", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.driver.id").value("driver-42"))
                .andExpect(jsonPath("$.driver.phone").value("+919988776655"))
                .andExpect(jsonPath("$.location.lat").value(12.9716))
                .andExpect(jsonPath("$.location.lng").value(77.5946))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getTracking_whenNotOwner_returnsNotFound() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, '+919999999991') ON CONFLICT DO NOTHING", otherUserId);
        Order order = createSavedOrder(otherUserId, OrderStatus.CONFIRMED);

        mockMvc.perform(get("/orders/{id}/tracking", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rescheduleOrder_withinWindow_swapsSlotLockAndUpdatesOrder() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.CONFIRMED);
        LocalDate today = LocalDate.now();

        String payload = objectMapper.writeValueAsString(Map.of(
                "newSlotId", slotId2,
                "slotDate", today.toString()
        ));

        mockMvc.perform(post("/orders/{id}/reschedule", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Read raw columns — a full aggregate load would touch LAZY line items outside a session
        java.util.UUID newSlotId = jdbcTemplate.queryForObject(
                "SELECT slot_id FROM orders WHERE id = ?", java.util.UUID.class, order.id());
        java.util.UUID newLockId = jdbcTemplate.queryForObject(
                "SELECT delivery_slot_lock_id FROM orders WHERE id = ?", java.util.UUID.class, order.id());
        assertThat(newSlotId).isEqualTo(slotId2);
        assertThat(newLockId).isNotEqualTo(order.deliverySlotLockId());

        // Verify old slot counter decremented, new slot counter incremented
        Integer oldCounter = jdbcTemplate.queryForObject(
                "SELECT current_count FROM delivery_slot_counters WHERE slot_id = ? AND slot_date = ?",
                Integer.class, slotId1, today);
        Integer newCounter = jdbcTemplate.queryForObject(
                "SELECT current_count FROM delivery_slot_counters WHERE slot_id = ? AND slot_date = ?",
                Integer.class, slotId2, today);
        assertThat(oldCounter).isEqualTo(0);
        assertThat(newCounter).isEqualTo(1);
    }

    @Test
    void rescheduleOrder_whenNotOwner_returnsNotFound() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, '+919999999992') ON CONFLICT DO NOTHING", otherUserId);
        Order order = createSavedOrder(otherUserId, OrderStatus.CONFIRMED);

        String payload = objectMapper.writeValueAsString(Map.of(
                "newSlotId", slotId2,
                "slotDate", LocalDate.now().toString()
        ));

        mockMvc.perform(post("/orders/{id}/reschedule", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    void rescheduleOrder_whenNotConfirmed_returns409Conflict() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.DISPATCHED);

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

    @Test
    void cancelOrder_withinWindow_cancelsOrderAndReleasesSlotLock() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.CONFIRMED);
        LocalDate today = LocalDate.now();

        mockMvc.perform(post("/orders/{id}/cancel", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.id());
        assertThat(status).isEqualTo(OrderStatus.CANCELLED.name());

        // Verify slot lock status released and slot counter decremented
        String lockStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM delivery_slot_locks WHERE id = ?",
                String.class, order.deliverySlotLockId());
        assertThat(lockStatus).isEqualTo(DeliverySlotLockStatus.RELEASED.name());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT current_count FROM delivery_slot_counters WHERE slot_id = ? AND slot_date = ?",
                Integer.class, slotId1, today);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void cancelOrder_whenNotOwner_returnsNotFound() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, '+919999999993') ON CONFLICT DO NOTHING", otherUserId);
        Order order = createSavedOrder(otherUserId, OrderStatus.CONFIRMED);

        mockMvc.perform(post("/orders/{id}/cancel", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void callDriver_whenDispatchedWithPhone_returnsCallInitiated() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.DISPATCHED);
        order = order.updateDriver("driver-99", "+919876543211");
        orderRepository.save(order);

        mockMvc.perform(post("/orders/{id}/call-driver", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALL_INITIATED"));
    }

    @Test
    void callDriver_whenDriverUnavailableOrNotDispatched_returnsBadRequest() throws Exception {
        Order order = createSavedOrder(userId, OrderStatus.CONFIRMED);

        mockMvc.perform(post("/orders/{id}/call-driver", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRIVER_UNAVAILABLE"));
    }

    @Test
    void callDriver_whenNotOwner_returnsNotFound() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, '+919999999994') ON CONFLICT DO NOTHING", otherUserId);
        Order order = createSavedOrder(otherUserId, OrderStatus.DISPATCHED);
        order = order.updateDriver("driver-99", "+919876543211");
        orderRepository.save(order);

        mockMvc.perform(post("/orders/{id}/call-driver", order.id())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void callDriver_whenOrderNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(post("/orders/{id}/call-driver", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isNotFound());
    }
}
