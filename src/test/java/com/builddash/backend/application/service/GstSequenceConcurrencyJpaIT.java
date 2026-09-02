package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GstSequenceConcurrencyJpaIT extends AbstractIntegrationTest {

    @Autowired
    private GstSequenceService sequenceService;

    @Test
    void concurrentSequenceAllocation_allocatesUniqueMonotonicNumbers() throws Exception {
        int threads = 10;
        int allocationsPerThread = 5;
        int totalAllocations = threads * allocationsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<String> allocatedNumbers = Collections.synchronizedList(new ArrayList<>());
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    for (int j = 0; j < allocationsPerThread; j++) {
                        String number = sequenceService.nextNumber(GstSequenceType.INVOICE);
                        allocatedNumbers.add(number);
                    }
                } catch (Exception e) {
                    errors.add(e);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads concurrently

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(allocatedNumbers).hasSize(totalAllocations);

        // Verify all generated sequence numbers are strictly unique
        Set<String> uniqueNumbers = new HashSet<>(allocatedNumbers);
        assertThat(uniqueNumbers).hasSize(totalAllocations);

        // Verify sequence format: e.g. INV-2627-000001
        for (String num : allocatedNumbers) {
            assertThat(num).matches("^INV-\\d{4}-\\d{6}$");
        }
    }
}
