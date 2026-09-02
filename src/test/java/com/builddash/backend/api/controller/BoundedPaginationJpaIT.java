package com.builddash.backend.api.controller;

import com.builddash.backend.domain.enums.LoginEventType;
import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.LoginEvent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.model.Review;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.LoginEventRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.QuestionRepository;
import com.builddash.backend.domain.port.ReviewRepository;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoundedPaginationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private LoginEventRepository loginEventRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String validToken;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        String phone = "+919988770001";
        JsonNode tokens = loginViaOtp(phone, "Device-Pagination-Test");
        validToken = "Bearer " + tokens.get("accessToken").asText();
        userId = userRepository.findByPhone(phone).orElseThrow().getId();
    }

    @Test
    void listOrders_boundedByPaginationParameters() throws Exception {
        UUID addressId = addressRepository.save(new Address(
                UUID.randomUUID(), userId, "HOME", "123 Line", null, "City", "State", "400001", 12.34, 56.78, true
        )).id();

        for (int i = 0; i < 5; i++) {
            Order order = new Order(
                    UUID.randomUUID(),
                    userId,
                    addressId,
                    UUID.fromString("11111111-1111-1111-1111-111111111101"),
                    LocalDate.now(),
                    new BigDecimal("100.00"),
                    OrderStatus.CONFIRMED,
                    UUID.randomUUID(),
                    Instant.now().plusSeconds(i),
                    null,
                    null,
                    List.of()
            );
            orderRepository.save(order);
        }

        mockMvc.perform(get("/orders?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/orders?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listReviews_boundedByPaginationParameters() throws Exception {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Tools', 'tools-" + UUID.randomUUID() + "')", categoryId);

        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) " +
                "VALUES (?, 'Drill', 'drill-" + UUID.randomUUID() + "', ?, 'ACTIVE', '8467', now(), now())", productId, categoryId);

        for (int i = 0; i < 5; i++) {
            Review review = new Review();
            review.setId(UUID.randomUUID());
            review.setProductId(productId);
            review.setUserId(userId);
            review.setRating(5);
            review.setComment("Review " + i);
            review.setStatus(ModerationStatus.APPROVED);
            review.setCreatedAt(Instant.now().plusSeconds(i));
            reviewRepository.save(review);
        }

        mockMvc.perform(get("/products/{id}/reviews?page=0&size=2", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/products/{id}/reviews?page=2&size=2", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listQuestions_boundedByPaginationParameters() throws Exception {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Paints', 'paints-" + UUID.randomUUID() + "')", categoryId);

        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) " +
                "VALUES (?, 'Paint', 'paint-" + UUID.randomUUID() + "', ?, 'ACTIVE', '3208', now(), now())", productId, categoryId);

        for (int i = 0; i < 5; i++) {
            Question q = new Question();
            q.setId(UUID.randomUUID());
            q.setProductId(productId);
            q.setUserId(userId);
            q.setBody("Question " + i);
            q.setCreatedAt(Instant.now().plusSeconds(i));
            questionRepository.save(q);
        }

        mockMvc.perform(get("/products/{id}/questions?page=0&size=2", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/products/{id}/questions?page=2&size=2", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listLoginHistory_boundedByPaginationParameters() throws Exception {
        for (int i = 0; i < 5; i++) {
            LoginEvent event = new LoginEvent();
            event.setId(UUID.randomUUID());
            event.setUserId(userId);
            event.setEventType(LoginEventType.OTP);
            event.setIpAddress("127.0.0.1");
            event.setDeviceFingerprint("Device-" + i);
            event.setCreatedAt(Instant.now().plusSeconds(i));
            loginEventRepository.save(event);
        }

        mockMvc.perform(get("/users/me/login-history?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/users/me/login-history?page=1&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/users/me/login-history?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listSupportTickets_boundedByPaginationParameters() throws Exception {
        for (int i = 0; i < 5; i++) {
            supportTicketRepository.save(new SupportTicket(
                    UUID.randomUUID(),
                    userId,
                    SupportTicketCategory.ORDER_ISSUE,
                    com.builddash.backend.domain.enums.SupportTicketStatus.OPEN,
                    "Subject " + i,
                    Instant.now().plusSeconds(3600),
                    Instant.now().plusSeconds(i),
                    Instant.now().plusSeconds(i)
            ));
        }

        mockMvc.perform(get("/support/tickets?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/support/tickets?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
