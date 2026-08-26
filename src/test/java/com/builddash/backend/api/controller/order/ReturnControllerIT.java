package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateReturnRequest;
import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.domain.service.ReturnRefundCalculator;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReturnControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String guestToken;
    private String vendorToken;
    private String adminToken;
    private UUID userId;
    private UUID addressId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING", userId, "+919" + String.format("%07d", Math.abs(userId.getLeastSignificantBits() % 10000000L)));
        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, deviceId, List.of("USER")).token();

        UUID guestId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now()) ON CONFLICT DO NOTHING", guestId);
        guestToken = "Bearer " + tokenIssuer.issueGuestToken(guestId).token();

        vendorToken = "Bearer " + tokenIssuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("VENDOR")).token();
        adminToken = "Bearer " + tokenIssuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("ADMIN")).token();

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())", addressId, userId);

        categoryId = UUID.randomUUID();
        String slug = "hardware-" + categoryId;
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7) ON CONFLICT (id) DO UPDATE SET return_window_days = 7", categoryId, slug);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now()) ON CONFLICT (id) DO NOTHING", productId, "cement-" + productId, categoryId);
        jdbcTemplate.update("INSERT INTO product_base_prices (product_id, price, created_at, updated_at) VALUES (?, 350.00, now(), now()) ON CONFLICT (product_id) DO NOTHING", productId);
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('2523', 'Cement', 28.00, 'Hardware', now(), now()) ON CONFLICT (hsn_code) DO NOTHING");
    }

    private Order createSampleOrder(UUID ownerId, OrderStatus orderStatus, Instant placedAt) {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID lockId = UUID.randomUUID();
        UUID addrId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())", addrId, ownerId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT (slot_id, slot_date) DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE') ON CONFLICT DO NOTHING", lockId, ownerId, slotId);

        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                productId,
                3,
                new BigDecimal("350.00"),
                new BigDecimal("294.00"),
                new BigDecimal("1344.00")
        );

        Order order = new Order(
                orderId,
                ownerId,
                addrId,
                slotId,
                LocalDate.now(),
                new BigDecimal("1344.00"),
                orderStatus,
                lockId,
                placedAt,
                "driver-1",
                "+919876543210",
                List.of(item)
        );

        return orderRepository.save(order);
    }

    @Test
    void createReturn_multipartHappyPath_createsReturnAndUploadsPhotos() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 2))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart1 = new MockMultipartFile(
                "photos",
                "damage1.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        MockMultipartFile photoPart2 = new MockMultipartFile(
                "photos",
                "damage2.png",
                "image/png",
                new byte[]{5, 6, 7, 8}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart1)
                        .file(photoPart2)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.orderId").value(order.id().toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.reason").value("DAMAGED"))
                .andExpect(jsonPath("$.photoKeys").isArray())
                .andExpect(jsonPath("$.lineItems[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.lineItems[0].quantityRequested").value(2))
                .andExpect(jsonPath("$.lineItems[0].refundAmount").value(896.00));
    }

    @Test
    void createReturn_zeroPhotos_returnsPhotosRequired400() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile emptyPhotoPart = new MockMultipartFile(
                "photos",
                "empty.jpg",
                "image/jpeg",
                new byte[]{}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(emptyPhotoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHOTOS_REQUIRED"));
    }

    @Test
    void createReturn_invalidPhotoType_returnsInvalidPhotoType400() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile invalidPart = new MockMultipartFile(
                "photos",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(invalidPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_TYPE"));
    }

    @Test
    void createReturn_orderNotDelivered_returnsInvalidOrderState409() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.CONFIRMED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    void createReturn_returnWindowExpired_returnsReturnWindowExpired400() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now().minus(Duration.ofDays(10)));

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RETURN_WINDOW_EXPIRED"));
    }

    @Test
    void createReturn_whenNotOwner_returnsNotFound404() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING",
                otherUserId, "+919" + String.format("%07d", Math.abs(otherUserId.getLeastSignificantBits() % 10000000L)));
        Order order = createSampleOrder(otherUserId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void createReturn_byGuest_returnsForbidden403() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturn_ownerHappyPath_andNonOwner404() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.WRONG_ITEM,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "wrong.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        String createRes = mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String returnIdStr = objectMapper.readTree(createRes).get("id").asText();
        UUID returnId = UUID.fromString(returnIdStr);

        // Owner gets 200
        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnIdStr))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        // Other user gets 404
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, '+919999999992', now(), now()) ON CONFLICT DO NOTHING", otherUserId);
        String otherUserToken = "Bearer " + tokenIssuer.issueAccessToken(otherUserId, UUID.randomUUID(), List.of("USER")).token();

        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RETURN_NOT_FOUND"));
    }

    @Test
    void rejectReturn_byVendorOrAdmin_returns200_andByUserOrGuest_returns403() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        String createRes = mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(createRes).get("id").asText());

        // Regular USER gets 403
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // GUEST gets 403
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // VENDOR gets 200
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void passQc_byVendorOrAdmin_transitionsToRefundInitiated_andByUser_returns403() throws Exception {
        Order order = createSampleOrder(userId, OrderStatus.DELIVERED, Instant.now());

        // Mock payment row so refund initiation succeeds
        jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, transaction_id, amount, status, payment_url, created_at) VALUES (?, ?, 'tx_ret_123', 1344.00, 'SUCCESS', 'http://pay', now())",
                UUID.randomUUID(), order.id()
        );

        CreateReturnRequest request = new CreateReturnRequest(
                ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1))
        );

        MockMultipartFile jsonPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        MockMultipartFile photoPart = new MockMultipartFile(
                "photos",
                "damage.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        String createRes = mockMvc.perform(multipart("/orders/{id}/return", order.id())
                        .file(jsonPart)
                        .file(photoPart)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(createRes).get("id").asText());

        // Advance return state to PICKED_UP
        jdbcTemplate.update("UPDATE returns SET status = 'PICKED_UP' WHERE id = ?", returnId);

        // Regular USER gets 403
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // ADMIN gets 200
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()))
                .andExpect(jsonPath("$.status").value("REFUND_INITIATED"));
    }
}
