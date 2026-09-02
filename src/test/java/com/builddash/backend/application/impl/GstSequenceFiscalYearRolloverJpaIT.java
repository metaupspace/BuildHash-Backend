package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H4.7 Real-PostgreSQL proof: GST sequence allocation scoped to (sequence_type, fiscal_year)
 * composite primary key supports dynamic Indian financial year calculation, concurrent
 * thread-safe allocation, and seamless April 1 fiscal year rollover without PK collision.
 */
class GstSequenceFiscalYearRolloverJpaIT extends AbstractIntegrationTest {

    @Autowired
    private GstSequenceService sequenceService;

    @Test
    void concurrentAllocationInSameFiscalYear_allocatesUniqueNumbers() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        Instant fy2627 = ZonedDateTime.of(2026, 9, 2, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                latch.await();
                return sequenceService.nextNumber(GstSequenceType.INVOICE, fy2627);
            }));
        }

        latch.countDown();
        pool.shutdown();
        boolean completed = pool.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }

        Set<String> uniqueNumbers = new HashSet<>(results);
        assertThat(uniqueNumbers).hasSize(threads);

        for (String num : results) {
            assertThat(num).startsWith("INV-2627-");
        }
    }

    @Test
    void fiscalYearRollover_startsNewSequenceWithoutPrimaryKeyCollision() {
        // 1. Current FY 2026-2027 allocation
        Instant march31 = ZonedDateTime.of(2027, 3, 31, 23, 59, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        String numFy2627 = sequenceService.nextNumber(GstSequenceType.INVOICE, march31);
        assertThat(numFy2627).startsWith("INV-2627-");

        // 2. Rollover to FY 2027-2028 on April 1
        Instant april1 = ZonedDateTime.of(2027, 4, 1, 0, 1, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        String numFy2728_1 = sequenceService.nextNumber(GstSequenceType.INVOICE, april1);
        assertThat(numFy2728_1).isEqualTo("INV-2728-000001");

        String numFy2728_2 = sequenceService.nextNumber(GstSequenceType.INVOICE, april1);
        assertThat(numFy2728_2).isEqualTo("INV-2728-000002");

        // 3. Credit Note sequence also rolls over independently
        String crnFy2728_1 = sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE, april1);
        assertThat(crnFy2728_1).isEqualTo("CRN-2728-000001");
    }
}
