package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres proof of the V24 partial unique (8.1-A): two concurrent createReturn
 * attempts for one order commit exactly one row; the loser receives the existing
 * ReturnAlreadyExistsException contract; the sequential and REJECTED-resubmission
 * semantics are unchanged.
 */
class ReturnConcurrencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);
    }

    private Order deliveredOrder() {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID lockId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 3,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        return orderRepository.save(new Order(orderId, userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, lockId, Instant.now(), null, null, List.of(item)));
    }

    private List<MultipartFile> photos() {
        return List.of(new MockMultipartFile("photos", "damage.jpg", "image/jpeg", new byte[]{1, 2, 3}));
    }

    private List<ReturnLineItemRequest> items() {
        return List.of(new ReturnLineItemRequest(productId, 1));
    }

    @Test
    void concurrentCreateReturn_exactlyOneRowCommits_loserGetsReturnAlreadyExists() throws Exception {
        Order order = deliveredOrder();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger alreadyExists = new AtomicInteger();

        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());
                        successes.incrementAndGet();
                    } catch (ReturnAlreadyExistsException e) {
                        alreadyExists.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(alreadyExists.get()).isEqualTo(threads - 1);
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM returns WHERE order_id = ? AND status <> 'REJECTED'", Integer.class, order.id());
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void sequentialSecondCreateReturn_stillBlockedByExistingGuard() {
        Order order = deliveredOrder();
        returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);
    }

    @Test
    void rejectedReturn_allowsNewActiveReturn() {
        Order order = deliveredOrder();
        com.builddash.backend.domain.model.Return first =
                returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());
        jdbcTemplate.update("UPDATE returns SET status = 'REJECTED' WHERE id = ?", first.id());

        com.builddash.backend.domain.model.Return second =
                returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());

        assertThat(second.status()).isEqualTo(ReturnStatus.REQUESTED);
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM returns WHERE order_id = ? AND status <> 'REJECTED'", Integer.class, order.id());
        assertThat(activeCount).isEqualTo(1);
        Integer rejectedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM returns WHERE order_id = ? AND status = 'REJECTED'", Integer.class, order.id());
        assertThat(rejectedCount).isEqualTo(1);
    }
}
