package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.model.CompanyContractPrice;
import com.builddash.backend.domain.port.CompanyContractPriceRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres proof of the V25 GiST exclusion constraint (ContractPriceOverlapJpaIT
 * sibling): overlapping windows for the same company+product cannot coexist — including
 * when two racing inserts both pass the application-level check. Disjoint windows and
 * different companies are unaffected.
 */
class CompanyContractPriceOverlapJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyContractPriceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID otherCompanyId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        otherCompanyId = UUID.randomUUID();
        productId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'A')", companyId);
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'B')", otherCompanyId);
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)",
                categoryId, "cat-" + categoryId);
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'P', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "p-" + productId, categoryId);
    }

    private CompanyContractPrice price(UUID company, Instant from, Instant to) {
        return new CompanyContractPrice(UUID.randomUUID(), company, productId,
                new BigDecimal("250.00"), from, to, null, null);
    }

    @Test
    void overlappingWindow_rejected_disjointWindowAccepted() {
        Instant now = Instant.now();
        repository.save(price(companyId, now.minusSeconds(1000), now.plusSeconds(1000)));

        // Overlap: same company+product, intersecting window
        assertThatThrownBy(() -> repository.save(price(companyId, now, now.plusSeconds(500))))
                .isInstanceOf(ContractPriceOverlapException.class);

        // Disjoint: after the first window closes
        repository.save(price(companyId, now.plusSeconds(1001), now.plusSeconds(2000)));

        // Different company: same window, no interaction
        repository.save(price(otherCompanyId, now.minusSeconds(1000), now.plusSeconds(1000)));
    }

    @Test
    void concurrentOverlappingInserts_exactlyOneCommits() throws Exception {
        Instant now = Instant.now();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();

        try {
            java.util.List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    try {
                        // Racing inserts of the SAME window: both pass the app-level
                        // check, the GiST constraint decides at flush
                        repository.save(price(companyId, now.minusSeconds(100), now.plusSeconds(1000)));
                        wins.incrementAndGet();
                    } catch (ContractPriceOverlapException e) {
                        overlaps.incrementAndGet();
                    }
                    return null;
                }));
            }
            gate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(wins.get()).isEqualTo(1);
        assertThat(overlaps.get()).isEqualTo(threads - 1);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_contract_pricing WHERE company_id = ? AND product_id = ?",
                Integer.class, companyId, productId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void findActive_respectsEffectiveWindow() {
        Instant now = Instant.now();
        CompanyContractPrice saved = repository.save(price(companyId, now.minusSeconds(1000), now.plusSeconds(1000)));

        assertThat(repository.findActive(companyId, productId, now.plusSeconds(500)))
                .hasValueSatisfying(active -> assertThat(active.id()).isEqualTo(saved.id()));
        assertThat(repository.findActive(companyId, productId, now.plusSeconds(5000))).isEmpty();
        assertThat(repository.findActive(otherCompanyId, productId, now.plusSeconds(500))).isEmpty();
    }
}
