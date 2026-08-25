package com.builddash.backend.api.controller.order;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DeliveryWebhookControllerIT extends AbstractIntegrationTest {

    private static final String API_KEY = "test-delivery-webhook-secret-key-12345";

    @DynamicPropertySource
    static void deliveryProperties(DynamicPropertyRegistry registry) {
        registry.add("delivery.webhook-api-key", () -> API_KEY);
    }

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

    private UUID userId;
    private UUID addressId;
    private UUID slotId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        addressId = UUID.randomUUID();
        slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");

        jdbcTemplate.update("INSERT INTO users (id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true) ON CONFLICT DO NOTHING", addressId, userId);
    }

    private String statusInDb(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private Order createSavedOrder(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        Order order = new Order(
                orderId,
                userId,
                addressId,
                slotId,
                LocalDate.now(),
                new BigDecimal("199.00"),
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
    void updateDeliveryStatus_missingApiKey_returnsUnauthorized() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        String payload = objectMapper.writeValueAsString(Map.of("status", "PACKED"));

        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest()); // missing header
    }

    @Test
    void updateDeliveryStatus_invalidApiKey_returnsUnauthorized() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        String payload = objectMapper.writeValueAsString(Map.of("status", "PACKED"));

        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateDeliveryStatus_validForwardJumps_success() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        // 1. CONFIRMED -> PACKED
        String packedPayload = objectMapper.writeValueAsString(Map.of(
                "status", "PACKED"
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(packedPayload))
                .andExpect(status().isOk());

        // Raw column reads — full aggregate loads would touch LAZY line items outside a session
        assertThat(statusInDb(order.id())).isEqualTo(OrderStatus.PACKED.name());

        // 2. PACKED -> DISPATCHED with driver and coordinates
        String dispatchedPayload = objectMapper.writeValueAsString(Map.of(
                "status", "DISPATCHED",
                "driverId", "driver-1",
                "driverPhone", "+919876543210",
                "latitude", 12.9716,
                "longitude", 77.5946
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchedPayload))
                .andExpect(status().isOk());

        assertThat(statusInDb(order.id())).isEqualTo(OrderStatus.DISPATCHED.name());
        assertThat(jdbcTemplate.queryForObject("SELECT driver_id FROM orders WHERE id = ?", String.class, order.id()))
                .isEqualTo("driver-1");
        assertThat(jdbcTemplate.queryForObject("SELECT driver_phone FROM orders WHERE id = ?", String.class, order.id()))
                .isEqualTo("+919876543210");

        // 3. DISPATCHED -> DELIVERED
        String deliveredPayload = objectMapper.writeValueAsString(Map.of(
                "status", "DELIVERED"
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveredPayload))
                .andExpect(status().isOk());

        assertThat(statusInDb(order.id())).isEqualTo(OrderStatus.DELIVERED.name());

        List<DeliveryTrackingEvent> events = trackingEventRepository.findAllByOrderId(order.id());
        assertThat(events).hasSize(3);
    }

    @Test
    void updateDeliveryStatus_idempotentSameStatus_doesNotDuplicateUnlessMoved() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        // Transition to DISPATCHED
        orderRepository.save(order.pack().dispatch("d-1", "+919999999999"));
        trackingEventRepository.save(new DeliveryTrackingEvent(
                UUID.randomUUID(), order.id(), OrderStatus.DISPATCHED, 12.9716, 77.5946, Instant.now()
        ));

        // Same status, same location -> 200 OK, event count unchanged
        String samePayload = objectMapper.writeValueAsString(Map.of(
                "status", "DISPATCHED",
                "latitude", 12.9716,
                "longitude", 77.5946
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(samePayload))
                .andExpect(status().isOk());

        List<DeliveryTrackingEvent> eventsAfterSame = trackingEventRepository.findAllByOrderId(order.id());
        assertThat(eventsAfterSame).hasSize(1);

        // Same status, MOVED location -> 200 OK, new event appended
        String movedPayload = objectMapper.writeValueAsString(Map.of(
                "status", "DISPATCHED",
                "latitude", 12.9720,
                "longitude", 77.5950
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movedPayload))
                .andExpect(status().isOk());

        List<DeliveryTrackingEvent> eventsAfterMoved = trackingEventRepository.findAllByOrderId(order.id());
        assertThat(eventsAfterMoved).hasSize(2);
        assertThat(eventsAfterMoved.get(0).latitude()).isEqualTo(12.9720);
    }

    @Test
    void updateDeliveryStatus_invalidJump_returns409Conflict() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        // Direct jump from CONFIRMED to DELIVERED is illegal
        String illegalPayload = objectMapper.writeValueAsString(Map.of(
                "status", "DELIVERED"
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(illegalPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    void updateDeliveryStatus_warehouseCancel_fromConfirmedPackedDispatched_succeeds() throws Exception {
        // CONFIRMED -> CANCELLED (warehouse cancel)
        Order fromConfirmed = createSavedOrder(OrderStatus.CONFIRMED);
        mockMvc.perform(put("/orders/{id}/status", fromConfirmed.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());
        assertThat(statusInDb(fromConfirmed.id())).isEqualTo(OrderStatus.CANCELLED.name());

        // PACKED -> CANCELLED
        Order fromPacked = createSavedOrder(OrderStatus.CONFIRMED);
        orderRepository.save(fromPacked.pack());
        mockMvc.perform(put("/orders/{id}/status", fromPacked.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());
        assertThat(statusInDb(fromPacked.id())).isEqualTo(OrderStatus.CANCELLED.name());

        // DISPATCHED -> CANCELLED
        Order fromDispatched = createSavedOrder(OrderStatus.CONFIRMED);
        orderRepository.save(fromDispatched.pack().dispatch("d-9", "+919999999998"));
        mockMvc.perform(put("/orders/{id}/status", fromDispatched.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());
        assertThat(statusInDb(fromDispatched.id())).isEqualTo(OrderStatus.CANCELLED.name());
    }

    @Test
    void updateDeliveryStatus_warehouseCancel_fromTerminalStates_returns409() throws Exception {
        // DELIVERED is terminal — no CANCELLED
        Order delivered = createSavedOrder(OrderStatus.CONFIRMED);
        orderRepository.save(delivered.pack().dispatch("d-8", "+919999999997").deliver());
        mockMvc.perform(put("/orders/{id}/status", delivered.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isConflict());

        // CANCELLED is terminal — no re-cancel
        Order cancelled = createSavedOrder(OrderStatus.CONFIRMED);
        orderRepository.save(cancelled.cancelConfirmed());
        mockMvc.perform(put("/orders/{id}/status", cancelled.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateDeliveryStatus_invalidCoordinates_returns400BadRequest() throws Exception {
        Order order = createSavedOrder(OrderStatus.CONFIRMED);

        String invalidCoordsPayload = objectMapper.writeValueAsString(Map.of(
                "status", "PACKED",
                "latitude", 95.0, // out of range
                "longitude", 77.0
        ));
        mockMvc.perform(put("/orders/{id}/status", order.id())
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCoordsPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"));
    }
}
