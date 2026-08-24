package com.builddash.backend.application.impl;

import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.infra.persistence.order.OrderEntity;
import com.builddash.backend.infra.persistence.order.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SweepVsWebhookJpaIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentWebhookService webhookService;

    @Autowired
    private StaleOrderSweepServiceImpl sweepService; // Autowire implementation to access sweepOrder(UUID)

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void concurrentSweepAndWebhook_onSameStaleOrder_exactlyOneWins() throws InterruptedException {
        // Assume seed data provides user, address, slot
        UUID orderId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING", userId, "+919999999999");
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO slot_configurations (id, start_time, end_time, capacity, is_active, created_at) VALUES (?, '09:00:00', '12:00:00', 50, true, now()) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at) VALUES (?, ?, ?, ?, now() + interval '1 hour') ON CONFLICT DO NOTHING", lockId, userId, slotId, java.time.LocalDate.now());
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Line 1', 'City', 'MH', '400000', now(), now())", addressId, userId);


        Order order = new Order(
                orderId,
                userId,
                addressId,
                slotId,
                java.time.LocalDate.now(),
                new java.math.BigDecimal("100.00"),
                OrderStatus.PAYMENT_PENDING,
                lockId,
                java.time.Instant.now(),
                null,
                null,
                java.util.List.of()
        );
        orderRepository.save(order);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        executor.submit(() -> {
            try {
                startLatch.await();
                webhookService.handleWebhook(orderId, "SUCCESS", "sig");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                sweepService.sweepOrder(orderId);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        OrderEntity finalState = orderJpaRepository.findById(orderId).orElseThrow();

        // Assert exactly one terminal state: winner committed, loser gracefully no-oped.
        if (finalState.getStatus() == OrderStatus.PAYMENT_PENDING) throw new RuntimeException("Both failed!");
        assertThat(finalState.getStatus()).isIn(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
    }
}
