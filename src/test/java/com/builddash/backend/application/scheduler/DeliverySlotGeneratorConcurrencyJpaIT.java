package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H5.5 Real-PostgreSQL proof:
 * Multiple concurrent scheduler instances running DeliverySlotGenerator execute without
 * unique constraint violations or rollbacks, guaranteed by atomic ON CONFLICT DO NOTHING.
 */
class DeliverySlotGeneratorConcurrencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private DeliverySlotGenerator slotGenerator;

    @Autowired
    private DeliverySlotCounterRepository counterRepository;

    @Autowired
    private SlotConfigurationRepository slotConfigurationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentSlotGeneration_completesWithoutUniqueConstraintAbort() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();

        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(7);

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                slotGenerator.generateSlotsForRange(start, end);
                successCount.incrementAndGet();
                return null;
            }));
        }

        startGate.countDown();
        pool.shutdown();
        boolean completed = pool.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        for (Future<Void> f : futures) {
            f.get();
        }

        // All 4 threads succeeded with 0 exceptions/rollbacks
        assertThat(successCount.get()).isEqualTo(threads);

        int activeSlotsCount = slotConfigurationRepository.findAllActive().size();
        int expectedRows = activeSlotsCount * 8; // start to end inclusive (8 days)

        Integer actualRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM delivery_slot_counters WHERE slot_date >= ? AND slot_date <= ?",
                Integer.class, start, end);
        assertThat(actualRows).isEqualTo(expectedRows);
    }
}
