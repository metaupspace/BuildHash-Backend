package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.InvalidReturnStateException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * H3.6 Real-PostgreSQL proof of row-locked state transitions:
 * Concurrent mutations serialize on findByIdForUpdate so duplicate transitions produce
 * exactly one winner with the loser receiving InvalidReturnStateException, and concurrent
 * passQc vs reject guarantees no duplicate refund claims or lost updates.
 */
class ReturnTransitionConcurrencyJpaIT extends AbstractIntegrationTest {

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

    private Order seedOrder() {
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
        Order order = orderRepository.save(new Order(orderId, userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, lockId, Instant.now(), null, null, List.of(item)));

        jdbcTemplate.update(
                "INSERT INTO payments (id, order_id, transaction_id, amount, status, payment_url, created_at) VALUES (?, ?, 'tx_ret_race', 1344.00, 'SUCCESS', 'http://pay', now())",
                UUID.randomUUID(), orderId
        );

        return order;
    }

    private Return seedReturn(UUID orderId, ReturnStatus status) {
        UUID returnId = UUID.randomUUID();
        ReturnLineItem retItem = new ReturnLineItem(UUID.randomUUID(), returnId, productId, 1, new BigDecimal("448.00"));
        Return ret = new Return(returnId, orderId, userId, status, ReturnReason.DAMAGED,
                List.of(), List.of(retItem), Instant.now(), Instant.now());
        return returnRepository.save(ret);
    }

    @Test
    void concurrentDoubleApprove_serializesWithExactlyOneWinner() throws Exception {
        Order order = seedOrder();
        Return ret = seedReturn(order.id(), ReturnStatus.REQUESTED);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger approvedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            Future<Void> f1 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.approve(ret.id(), userId, List.of("VENDOR"));
                    approvedCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            Future<Void> f2 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.approve(ret.id(), userId, List.of("ADMIN"));
                    approvedCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            startGate.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(approvedCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        Return finalReturn = returnRepository.findById(ret.id()).orElseThrow();
        assertThat(finalReturn.status()).isEqualTo(ReturnStatus.APPROVED);
    }

    @Test
    void concurrentDoubleReject_serializesWithExactlyOneWinner() throws Exception {
        Order order = seedOrder();
        Return ret = seedReturn(order.id(), ReturnStatus.REQUESTED);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger rejectedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            Future<Void> f1 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.reject(ret.id(), userId, List.of("VENDOR"));
                    rejectedCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            Future<Void> f2 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.reject(ret.id(), userId, List.of("ADMIN"));
                    rejectedCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            startGate.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(rejectedCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        Return finalReturn = returnRepository.findById(ret.id()).orElseThrow();
        assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void concurrentQcPassVsReject_fromPickedUp_serializesWithExactlyOneWinner() throws Exception {
        Order order = seedOrder();
        Return ret = seedReturn(order.id(), ReturnStatus.PICKED_UP);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger qcPassCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            Future<Void> f1 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.passQc(ret.id(), userId, List.of("VENDOR"));
                    qcPassCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            Future<Void> f2 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.reject(ret.id(), userId, List.of("ADMIN"));
                    rejectedCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            startGate.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(qcPassCount.get() + rejectedCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        Return finalReturn = returnRepository.findById(ret.id()).orElseThrow();
        if (qcPassCount.get() == 1) {
            assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REFUND_INITIATED);
            Integer refunds = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, ret.id());
            assertThat(refunds).isEqualTo(1);
        } else {
            assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REJECTED);
            Integer refunds = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, ret.id());
            assertThat(refunds).isEqualTo(0);
        }
    }

    @Test
    void concurrentDoubleQcPass_serializesWithExactlyOneWinnerAndOneRefundClaim() throws Exception {
        Order order = seedOrder();
        Return ret = seedReturn(order.id(), ReturnStatus.PICKED_UP);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger qcPassCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            Future<Void> f1 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.passQc(ret.id(), userId, List.of("VENDOR"));
                    qcPassCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            Future<Void> f2 = pool.submit(() -> {
                startGate.await();
                try {
                    returnService.passQc(ret.id(), userId, List.of("ADMIN"));
                    qcPassCount.incrementAndGet();
                } catch (InvalidReturnStateException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            });

            startGate.countDown();
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(qcPassCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        Return finalReturn = returnRepository.findById(ret.id()).orElseThrow();
        assertThat(finalReturn.status()).isEqualTo(ReturnStatus.REFUND_INITIATED);
        Integer refunds = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, ret.id());
        assertThat(refunds).isEqualTo(1);
    }
}
