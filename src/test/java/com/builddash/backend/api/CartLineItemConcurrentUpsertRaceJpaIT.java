package com.builddash.backend.api;

import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.builddash.backend.support.ApprovalTestFixtures.seedProductWithCategory;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2.9 on real Postgres: concurrent identical adds used to race select-then-insert into
 * uq_cart_line_item_product and surface a DataIntegrityViolationException 500. One SQL
 * upsert on (cart_id, product_id) must collapse any number of concurrent adds into one
 * row. The cart is pre-created via a different product so the race isolates the
 * line-item statement (cart creation is a separate concern). The second test pins the
 * preserved quantity semantics under concurrency: last write wins, one row, a value
 * that was actually written.
 */
class CartLineItemConcurrentUpsertRaceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CartService cartService;
    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID seedUserWithCart() {
        UUID userId = seedUser(jdbc);
        UUID[] filler = seedProductWithCategory(jdbc);
        productBasePriceRepository.save(filler[0], new BigDecimal("10.00"));
        cartService.upsertItem(userId, null, filler[0], 1, null); // creates the PRIMARY cart
        return userId;
    }

    @Test
    void concurrentIdenticalAdds_singleRowNoIntegrityError() throws Exception {
        UUID userId = seedUserWithCart();
        UUID[] product = seedProductWithCategory(jdbc);
        productBasePriceRepository.save(product[0], new BigDecimal("10.00"));
        UUID cartId = primaryCartId(userId);

        int threads = 8;
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    await(start);
                    try {
                        cartService.upsertItem(userId, null, product[0], 2, null);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        throw e;
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures.get()).as("no integrity-error 500s under concurrency").isZero();

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM cart_line_items WHERE cart_id = ? AND product_id = ?",
                Integer.class, cartId, product[0]);
        assertThat(rows).as("one (cart_id, product_id) => exactly one row").isEqualTo(1);

        Integer quantity = jdbc.queryForObject(
                "SELECT quantity FROM cart_line_items WHERE cart_id = ? AND product_id = ?",
                Integer.class, cartId, product[0]);
        assertThat(quantity).isEqualTo(2);
    }

    @Test
    void concurrentDifferentQuantities_singleRowLastWriteWins() throws Exception {
        UUID userId = seedUserWithCart();
        UUID[] product = seedProductWithCategory(jdbc);
        productBasePriceRepository.save(product[0], new BigDecimal("10.00"));
        UUID cartId = primaryCartId(userId);

        race(
                () -> cartService.upsertItem(userId, null, product[0], 2, null),
                () -> cartService.upsertItem(userId, null, product[0], 5, null));

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM cart_line_items WHERE cart_id = ? AND product_id = ?",
                Integer.class, cartId, product[0]);
        Integer quantity = jdbc.queryForObject(
                "SELECT quantity FROM cart_line_items WHERE cart_id = ? AND product_id = ?",
                Integer.class, cartId, product[0]);
        assertThat(rows).isEqualTo(1);
        // Last write wins — never a sum, never a duplicate row with the other value.
        assertThat(quantity).isIn(2, 5);
    }

    private UUID primaryCartId(UUID userId) {
        return jdbc.queryForObject(
                "SELECT id FROM carts WHERE user_id = ? AND cart_type = 'PRIMARY'", UUID.class, userId);
    }

    private void race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    pool.submit(() -> {
                        await(start);
                        a.run();
                    }),
                    pool.submit(() -> {
                        await(start);
                        b.run();
                    }));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
