package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.domain.port.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSweeperTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationDispatchQueue dispatchQueue;

    private NotificationSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new NotificationSweeper(logRepository, dispatchQueue);
        ReflectionTestUtils.setField(sweeper, "stuckAfterMinutes", 10L);
    }

    private NotificationLog row(UUID id) {
        NotificationLog row = new NotificationLog();
        row.setId(id);
        row.setUserId(UUID.randomUUID());
        row.setRecipientPhone("+911234567890");
        row.setChannel(NotificationChannel.WHATSAPP);
        row.setEventType(NotificationEventType.ORDER_PACKED);
        row.setReferenceId(UUID.randomUUID());
        return row;
    }

    @Test
    void sweep_usesConfiguredStuckThresholdAsCutoff() {
        Instant before = Instant.now();
        when(logRepository.findStalePending(any())).thenReturn(List.of());

        sweeper.sweep();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(logRepository).findStalePending(cutoffCaptor.capture());
        Instant after = Instant.now();
        assertThat(cutoffCaptor.getValue()).isBetween(before.minus(Duration.ofMinutes(10)), after.minus(Duration.ofMinutes(10)));
    }

    @Test
    void stuckRow_reenqueuedWithExactOriginalArgs() {
        NotificationLog stuck = row(UUID.randomUUID());
        when(logRepository.findStalePending(any())).thenReturn(List.of(stuck));

        sweeper.sweep();

        verify(dispatchQueue).enqueue(stuck.getId(), stuck.getChannel(), stuck.getRecipientPhone(),
                stuck.getEventType(), stuck.getReferenceId());
    }

    @Test
    void enqueueFailure_loggedNotPropagated_siblingStillSwept() {
        NotificationLog first = row(UUID.randomUUID());
        NotificationLog second = row(UUID.randomUUID());
        when(logRepository.findStalePending(any())).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("broker down")).when(dispatchQueue)
                .enqueue(first.getId(), first.getChannel(), first.getRecipientPhone(), first.getEventType(), first.getReferenceId());

        assertThatCode(() -> sweeper.sweep()).doesNotThrowAnyException();

        verify(dispatchQueue).enqueue(second.getId(), second.getChannel(), second.getRecipientPhone(),
                second.getEventType(), second.getReferenceId());
    }
}
