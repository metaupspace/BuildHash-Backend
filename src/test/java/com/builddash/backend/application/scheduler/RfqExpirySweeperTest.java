package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.RfqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfqExpirySweeperTest {

    private RfqRepository rfqRepository;
    private RfqExpirySweeper sweeper;

    @BeforeEach
    void setUp() {
        rfqRepository = mock(RfqRepository.class);
        sweeper = new RfqExpirySweeper(rfqRepository);
    }

    @Test
    void sweep_runsSingleConditionalUpdateWithNow() {
        when(rfqRepository.expireOpenBefore(any(Instant.class))).thenReturn(3);
        Instant before = Instant.now();

        sweeper.sweep();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        // One bulk conditional UPDATE per sweep — no per-row loop, no row data returned.
        verify(rfqRepository, times(1)).expireOpenBefore(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(before);
    }

    @Test
    void sweep_zeroExpired_isQuietSuccess() {
        when(rfqRepository.expireOpenBefore(any(Instant.class))).thenReturn(0);

        sweeper.sweep();

        verify(rfqRepository).expireOpenBefore(any(Instant.class));
    }
}
