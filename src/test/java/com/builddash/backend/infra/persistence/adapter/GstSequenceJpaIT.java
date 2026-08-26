package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.application.service.GstSequenceService;
import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GstSequenceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private GstSequenceService sequenceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void concurrentSequenceAllocation_allocatesGaplessAndUniqueNumbers() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(5, TimeUnit.SECONDS);
                return sequenceService.nextNumber(GstSequenceType.INVOICE);
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }

        Set<String> uniqueResults = new HashSet<>(results);
        assertThat(uniqueResults).hasSize(threadCount);

        List<Integer> sequenceNumbers = results.stream()
                .map(s -> Integer.parseInt(s.replace("INV-2627-", "")))
                .sorted()
                .toList();

        for (int i = 0; i < sequenceNumbers.size(); i++) {
            assertThat(sequenceNumbers.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void abortedTransaction_doesNotConsumeSequenceNumber() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Long currentVal = jdbcTemplate.queryForObject(
                "SELECT current_val FROM gst_sequences WHERE sequence_type = 'CREDIT_NOTE'", Long.class);
        long base = currentVal != null ? currentVal : 0L;
        String expectedNum = String.format("CRN-2627-%06d", base + 1);

        assertThatThrownBy(() -> {
            txTemplate.execute(status -> {
                String num = sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE);
                assertThat(num).isEqualTo(expectedNum);
                throw new RuntimeException("Forced rollback inside transaction");
            });
        }).isInstanceOf(RuntimeException.class).hasMessageContaining("Forced rollback");

        String numAfterRollback = sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE);
        assertThat(numAfterRollback).isEqualTo(expectedNum);
    }
}
