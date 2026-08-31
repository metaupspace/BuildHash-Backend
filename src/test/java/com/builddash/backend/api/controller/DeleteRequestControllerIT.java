package com.builddash.backend.api.controller;

import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeleteRequestControllerIT extends AbstractIntegrationTest {

    private static final String PATH = "/users/me/delete-request";

    /** Unique phone per test — all tests share one context/DB, and a pending request from an
     *  earlier test would 409 the first POST of the next one. */
    private JsonNode login() throws Exception {
        return loginViaOtp("+9177001" + String.format("%06d", System.nanoTime() % 1000000),
                "Delete-Request-Device");
    }

    @Test
    void happyPath_202WithDeletionScheduledAtPlusGrace() throws Exception {
        String token = login().get("accessToken").asText();

        String body = mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deletionScheduledAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // 30-day grace: scheduled ~30d from now (±5min slack).
        Instant expected = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant actual = Instant.parse(objectMapper.readTree(body).get("deletionScheduledAt").asText());
        assertThat(actual).isAfter(expected.minus(5, ChronoUnit.MINUTES))
                .isBefore(expected.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void duplicate_returns409WithPendingCode() throws Exception {
        String token = login().get("accessToken").asText();
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETE_REQUEST_PENDING"));
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(PATH).contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentDuplicateRequests_exactlyOne202One409_never500() throws Exception {
        String token = login().get("accessToken").asText();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<Integer>> calls = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> (Callable<Integer>) () -> {
                    startGate.await();
                    return mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token))
                            .andReturn().getResponse().getStatus();
                })
                .toList();
        List<Future<Integer>> futures = calls.stream().map(pool::submit).toList();
        startGate.countDown();

        List<Integer> statuses = futures.stream().map(f -> {
            try {
                return f.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
        pool.shutdown();

        // The DB partial unique index (user_id WHERE status='PENDING') is the backstop: the
        // racing loser's DataIntegrityViolationException translates to 409, never a 500.
        assertThat(statuses).containsExactlyInAnyOrder(202, 409);
        assertThat(statuses).doesNotContain(500);
    }
}
