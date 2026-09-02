package com.builddash.backend.api;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.DomainException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedProductWithCategory;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2.1 on real Postgres: the atomic carts.consumed_at claim is the ONLY thing standing
 * between one B2B draft and two paid orders. Two concurrent checkouts of the same draft
 * (distinct idempotency keys, so both take the fresh-order path) must produce exactly
 * one order, one winner, and one CART_ALREADY_CONSUMED rejection. The failed-checkout
 * test pins the other half of the invariant: a checkout that dies AFTER the claim must
 * roll the claim back and leave the draft usable.
 */
class B2bDraftCartConcurrentCheckoutRaceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AddressService addressService;
    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private record CheckoutSetup(UUID userId, UUID siteId, UUID addressId, UUID slotId,
                                 LocalDate slotDate, UUID cartId, BigDecimal total) {
    }

    private CheckoutSetup seedDraft(String phoneSuffix) {
        UUID companyId = seedCompany(jdbc, "DraftRaceCo-" + phoneSuffix);
        UUID userId = seedUser(jdbc);
        UUID siteId = seedSite(jdbc, companyId, "Main", true);
        seedMember(jdbc, companyId, userId, "PROCUREMENT_MANAGER", List.of(siteId));
        org.springframework.jdbc.core.JdbcTemplate j = jdbc;
        j.update("INSERT INTO company_role_permissions (company_id, role, permission) VALUES (?, ?, 'ORDER_CREATE') "
                + "ON CONFLICT DO NOTHING", companyId, "PROCUREMENT_MANAGER");

        UUID[] product = seedProductWithCategory(jdbc);
        productBasePriceRepository.save(product[0], new BigDecimal("10.00"));

        PricedCart draft = cartService.createB2bDraftCart(companyId, userId, UUID.randomUUID(),
                List.of(new CartLineItem(UUID.randomUUID(), null, product[0], 3, null)));

        UUID addressId = addressService.createAddress(userId, "SITE", "Plot 42", null, "Nagpur", "MH", "440001").id();
        UUID slotId = seedCounter(jdbc, LocalDate.now().plusDays(1), 50, 0);
        return new CheckoutSetup(userId, siteId, addressId, slotId, LocalDate.now().plusDays(1), draft.id(), draft.finalTotal());
    }

    @Test
    void concurrentCheckoutOfSameDraft_exactlyOneOrderOneWinner() throws Exception {
        CheckoutSetup s = seedDraft("race");
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger alreadyConsumed = new AtomicInteger();
        List<String> keys = List.of("race-key-a", "race-key-b");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<? extends Future<?>> futures = keys.stream().map(key -> pool.submit(() -> {
                await(start);
                try {
                    orderService.create(s.userId(), s.addressId(), s.slotId(), s.slotDate(),
                            s.total(), s.cartId(), s.siteId(), key);
                    ok.incrementAndGet();
                } catch (BadRequestException e) {
                    if ("CART_ALREADY_CONSUMED".equals(e.getCode())) {
                        alreadyConsumed.incrementAndGet();
                    } else {
                        throw e;
                    }
                }
            })).toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(ok.get()).as("exactly one checkout wins the claim").isEqualTo(1);
        assertThat(alreadyConsumed.get()).as("the loser is rejected, not queued").isEqualTo(1);

        Integer orders = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ? AND company_id IS NOT NULL", Integer.class, s.userId());
        assertThat(orders).as("one draft can never become two orders").isEqualTo(1);

        Integer consumedAt = jdbc.queryForObject(
                "SELECT count(*) FROM carts WHERE id = ? AND consumed_at IS NOT NULL", Integer.class, s.cartId());
        assertThat(consumedAt).as("successful checkout consumes exactly once").isEqualTo(1);
    }

    @Test
    void failedCheckoutAfterClaim_rollsBackClaim_draftStaysUsable() {
        CheckoutSetup s = seedDraft("reuse");

        // Price mismatch aborts the checkout AFTER the claim (claim runs before
        // createIntent's price validation) — the transaction must take the claim with it.
        BigDecimal wrongTotal = s.total().add(new BigDecimal("1.00"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        orderService.create(s.userId(), s.addressId(), s.slotId(), s.slotDate(),
                                wrongTotal, s.cartId(), s.siteId(), "reuse-key-fail"))
                .isInstanceOf(DomainException.class);

        Integer consumedAt = jdbc.queryForObject(
                "SELECT count(*) FROM carts WHERE id = ? AND consumed_at IS NULL", Integer.class, s.cartId());
        assertThat(consumedAt).as("failed checkout must roll the claim back").isEqualTo(1);

        // And the draft still checks out cleanly afterwards.
        orderService.create(s.userId(), s.addressId(), s.slotId(), s.slotDate(),
                s.total(), s.cartId(), s.siteId(), "reuse-key-ok");
        Integer orders = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, s.userId());
        assertThat(orders).isEqualTo(1);
        Integer nowConsumed = jdbc.queryForObject(
                "SELECT count(*) FROM carts WHERE id = ? AND consumed_at IS NOT NULL", Integer.class, s.cartId());
        assertThat(nowConsumed).isEqualTo(1);
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
