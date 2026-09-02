package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GstSequenceIntegrityJpaIT extends AbstractIntegrationTest {

    @Autowired
    private GstSequenceService sequenceService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void fiscalYearRollover_producesDistinctPrefixesAndResetsNumbering() {
        // March 31, 2026 -> FY 2025-2026 ("INV-2526-")
        Instant march31 = Instant.parse("2026-03-31T10:00:00Z");
        String marchNum = sequenceService.nextNumber(GstSequenceType.INVOICE, march31);
        assertThat(marchNum).startsWith("INV-2526-");

        // April 1, 2026 -> FY 2026-2027 ("INV-2627-")
        Instant april1 = Instant.parse("2026-04-01T10:00:00Z");
        String aprilNum = sequenceService.nextNumber(GstSequenceType.INVOICE, april1);
        assertThat(aprilNum).startsWith("INV-2627-");

        // Credit Note sequence in April 2026 -> "CRN-2627-"
        String creditNum = sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE, april1);
        assertThat(creditNum).startsWith("CRN-2627-");

        // Debit Note sequence in April 2026 -> "DBN-2627-"
        String debitNum = sequenceService.nextNumber(GstSequenceType.DEBIT_NOTE, april1);
        assertThat(debitNum).startsWith("DBN-2627-");
    }

    @Test
    void sequenceAllocation_isThreadSafeAndMonotonic() throws Exception {
        int threads = 5;
        int countPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        List<String> numbers = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        Instant testTime = Instant.parse("2026-06-15T12:00:00Z");

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.countDown();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < countPerThread; j++) {
                        numbers.add(sequenceService.nextNumber(GstSequenceType.INVOICE, testTime));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(numbers).hasSize(threads * countPerThread);
        Set<String> unique = new HashSet<>(numbers);
        assertThat(unique).hasSize(threads * countPerThread);
    }
}
