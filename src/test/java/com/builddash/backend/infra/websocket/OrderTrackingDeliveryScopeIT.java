package com.builddash.backend.infra.websocket;

import com.builddash.backend.application.service.OrderTrackingService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN_PHASE5.md Section 5: a broadcast on /topic/orders/{id} reaches the client subscribed
 * to that order, and NOT a client subscribed to a different order's topic. Two real owners,
 * two real STOMP sessions through the real WebSocketAuthChannelInterceptor, one real webhook
 * transition — no mocks on the delivery path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderTrackingDeliveryScopeIT extends AbstractIntegrationTest {

    private static final String API_KEY = "test-delivery-webhook-secret-key-12345";
    private static final UUID SLOT_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");

    @DynamicPropertySource
    static void deliveryProperties(DynamicPropertyRegistry registry) {
        registry.add("delivery.webhook-api-key", () -> API_KEY);
    }

    @Autowired
    private OrderTrackingService orderTrackingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.builddash.backend.domain.port.OrderRepository orderRepository;

    @Autowired
    private com.builddash.backend.domain.port.UserRepository userRepository;

    @LocalServerPort
    private int port;

    private UUID orderA;
    private UUID orderB;

    @BeforeEach
    void setUp() {
        orderA = seedConfirmedOrder("+911111919201");
        orderB = seedConfirmedOrder("+911111919202");
    }

    @Test
    void broadcast_reachesOnlyTheSubscribedOrdersTopic() throws Exception {
        CountDownLatch receivedA = new CountDownLatch(1);
        CountDownLatch receivedB = new CountDownLatch(1);
        String[] payloadA = new String[1];

        StompSession sessionA = null;
        StompSession sessionB = null;
        try {
            sessionA = connectAsOwner("+911111919201");
            sessionB = connectAsOwner("+911111919202");
            sessionA.subscribe("/topic/orders/" + orderA, frameHandler(receivedA, payloadA));
            sessionB.subscribe("/topic/orders/" + orderB, frameHandler(receivedB, new String[1]));

            // Real webhook path for order A only
            orderTrackingService.updateDeliveryStatus(orderA, OrderStatus.PACKED,
                    null, null, null, null, API_KEY);

            assertThat(receivedA.await(10, TimeUnit.SECONDS))
                    .as("owner of order A must receive the broadcast within the bounded wait")
                    .isTrue();

            // Grace window after A's delivery: B must stay silent
            assertThat(receivedB.await(750, TimeUnit.MILLISECONDS))
                    .as("owner of order B must NOT receive a broadcast for order A")
                    .isFalse();

            JsonNode frame = objectMapper.readTree(payloadA[0]);
            assertThat(frame.get("orderId").asText()).isEqualTo(orderA.toString());
            assertThat(frame.get("status").asText()).isEqualTo("PACKED");
        } finally {
            if (sessionA != null) sessionA.disconnect();
            if (sessionB != null) sessionB.disconnect();
        }
    }

    private StompFrameHandler frameHandler(CountDownLatch latch, String[] captured) {
        return new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                captured[0] = new String((byte[]) payload);
                latch.countDown();
            }
        };
    }

    /** Real OTP flow — no test-only token factory. */
    private StompSession connectAsOwner(String phone) throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        String otp = smsGateway.lastOtpFor(phone);
        MvcResult result = mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + otp + "\",\"deviceFingerprint\":\"WsScope-Device\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);
        return stompClient
                .connect("ws://localhost:" + port + "/ws", new org.springframework.web.socket.WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private UUID seedConfirmedOrder(String phone) {
        // Repository save (not raw SQL): the adapter populates phone_idx, which the OTP
        // login in connectAsOwner resolves via findByPhoneIdx — raw rows have NULL idx and
        // would cause the login to mint a second, different user (the Phase 8 fixture trap).
        // @UuidGenerator discards the preset id, so the generated id is read back.
        com.builddash.backend.domain.model.User user = new com.builddash.backend.domain.model.User();
        user.setPhone(phone);
        UUID userId = userRepository.save(user).getId();
        jdbcTemplate.update(
                "INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)",
                UUID.randomUUID(), userId);
        Order order = new Order(
                UUID.randomUUID(), userId, jdbcTemplate.queryForObject(
                        "SELECT id FROM addresses WHERE user_id = ? LIMIT 1", UUID.class, userId),
                SLOT_ID, LocalDate.now(), new BigDecimal("199.00"),
                OrderStatus.CONFIRMED, UUID.randomUUID(), Instant.now(), null, null, java.util.List.of()
        );
        return orderRepository.save(order).id();
    }
}
