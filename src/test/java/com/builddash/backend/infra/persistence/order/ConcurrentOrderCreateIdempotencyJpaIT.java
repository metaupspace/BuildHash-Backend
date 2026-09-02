package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentOrderCreateIdempotencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.builddash.backend.application.scheduler.DeliverySlotGenerator deliverySlotGenerator;

    private UUID userId;
    private UUID addressId;
    private UUID productId;
    private final UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");

    @BeforeEach
    void setUp() {
        deliverySlotGenerator.generateSlotsForRange(LocalDate.now(), LocalDate.now().plusDays(1));

        com.builddash.backend.domain.model.User user = new com.builddash.backend.domain.model.User();
        String phone = "+9198" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000));
        user.setPhone(phone);
        userId = userRepository.save(user).getId();

        addressId = addressRepository.save(new com.builddash.backend.domain.model.Address(
                UUID.randomUUID(), userId, "HOME", "123 Street", null, "City", "State", "400001", 12.34, 56.78, true
        )).id();

        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Cat', ?)", categoryId, "cat-" + UUID.randomUUID());

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) " +
                "VALUES (?, 'Steel Bar', ?, ?, 'ACTIVE', '7214', now(), now())", productId, "steel-" + UUID.randomUUID(), categoryId);
        jdbcTemplate.update("INSERT INTO product_base_prices (product_id, price, created_at, updated_at) VALUES (?, 100.00, now(), now())", productId);
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('7214', 'Steel', 18.00, 'Cat', now(), now()) ON CONFLICT DO NOTHING");

        cartService.upsertItem(userId, null, productId, 1, null);
    }

    @Test
    void concurrentOrderCreate_withSameIdempotencyKey_createsExactlyOneOrderAndZeroOrphans() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<OrderResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    OrderResult res = orderService.create(
                            userId, addressId, slotId, LocalDate.now(), null, null, null, idempotencyKey
                    );
                    results.add(res);
                } catch (Exception e) {
                    errors.add(e);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release both threads simultaneously

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(results).hasSize(2);

        UUID orderId1 = results.get(0).order().id();
        UUID orderId2 = results.get(1).order().id();
        assertThat(orderId1).isEqualTo(orderId2);

        // Verify exactly one order exists in the database
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, userId);
        assertThat(orderCount).isEqualTo(1);

        // Verify exactly one delivery slot lock exists for this user
        Integer lockCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM delivery_slot_locks WHERE user_id = ?", Integer.class, userId);
        assertThat(lockCount).isEqualTo(1);
    }
}
