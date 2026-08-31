package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.infra.config.OrderIdempotencyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cutoff derivation + invocation proof (PLAN_PHASE8 decision 10). The purge is table
 * hygiene, never correctness — but its cutoff must match the read filter's window or the
 * sweep would delete keys the read path still honors.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyPurgeSchedulerTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    /** Real instance: the window value under test, default and override. */
    @Spy
    private OrderIdempotencyProperties properties = new OrderIdempotencyProperties();

    @InjectMocks
    private IdempotencyPurgeScheduler scheduler;

    @Test
    void purge_defaultWindow24h_cutoffDerivedAndPassedToRepo() {
        scheduler.purge();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(idempotencyKeyRepository).deleteCreatedBefore(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), Instant.now()))
                .isCloseTo(Duration.ofHours(24), Duration.ofSeconds(5));
    }

    @Test
    void purge_windowOverride_respectedInCutoff() {
        properties.setIdempotencyWindowHours(72);

        scheduler.purge();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(idempotencyKeyRepository).deleteCreatedBefore(cutoff.capture());
        assertThat(Duration.between(cutoff.getValue(), Instant.now()))
                .isCloseTo(Duration.ofHours(72), Duration.ofSeconds(5));
    }

    @Test
    void purge_zeroRemoved_skipsCountLogRepoStillInvoked() {
        when(idempotencyKeyRepository.deleteCreatedBefore(any(Instant.class))).thenReturn(0);

        scheduler.purge();

        verify(idempotencyKeyRepository).deleteCreatedBefore(any(Instant.class));
    }

    @Test
    void purge_rowsRemoved_countLogPathExercised() {
        when(idempotencyKeyRepository.deleteCreatedBefore(any(Instant.class))).thenReturn(17);

        scheduler.purge();

        verify(idempotencyKeyRepository).deleteCreatedBefore(any(Instant.class));
    }
}
