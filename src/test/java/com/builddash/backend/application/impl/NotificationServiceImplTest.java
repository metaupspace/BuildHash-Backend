package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationDispatchQueue dispatchQueue;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(logRepository, userRepository, dispatchQueue);
    }

    @Test
    void duplicateGuard_skipsWithoutUserLookup() {
        UUID userId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        when(logRepository.existsByEventTypeAndReferenceIdAndUserId(NotificationEventType.ORDER_PACKED, referenceId, userId)).thenReturn(true);

        service.notify(userId, NotificationEventType.ORDER_PACKED, referenceId);

        verifyNoInteractions(userRepository, dispatchQueue);
        verify(logRepository, never()).save(any());
    }

    @Test
    void missingUser_skipsCleanly() {
        UUID userId = UUID.randomUUID();
        when(logRepository.existsByEventTypeAndReferenceIdAndUserId(any(), any(), any())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        service.notify(userId, NotificationEventType.INVOICE_READY, UUID.randomUUID());

        verifyNoInteractions(dispatchQueue);
        verify(logRepository, never()).save(any());
    }

    @Test
    void userWithoutPhone_skipsCleanly() {
        UUID userId = UUID.randomUUID();
        User phoneless = new User();
        phoneless.setId(userId);
        when(logRepository.existsByEventTypeAndReferenceIdAndUserId(any(), any(), any())).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(phoneless));

        service.notify(userId, NotificationEventType.REFUND_COMPLETED, UUID.randomUUID());

        verifyNoInteractions(dispatchQueue);
        verify(logRepository, never()).save(any());
    }

    @Test
    void happyPath_writesPendingRowWithPhoneSnapshotAndEnqueues() {
        UUID userId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setPhone("+911234567890");
        when(logRepository.existsByEventTypeAndReferenceIdAndUserId(NotificationEventType.ORDER_PACKED, referenceId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(logRepository.save(any())).thenAnswer(invocation -> {
            NotificationLog saved = invocation.getArgument(0);
            saved.setId(logId);
            return saved;
        });

        service.notify(userId, NotificationEventType.ORDER_PACKED, referenceId);

        ArgumentCaptor<NotificationLog> rowCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(rowCaptor.capture());
        NotificationLog row = rowCaptor.getValue();
        assertThat(row.getUserId()).isEqualTo(userId);
        assertThat(row.getRecipientPhone()).isEqualTo("+911234567890");
        assertThat(row.getChannel()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(row.getEventType()).isEqualTo(NotificationEventType.ORDER_PACKED);
        assertThat(row.getReferenceId()).isEqualTo(referenceId);
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);

        verify(dispatchQueue).enqueue(logId, NotificationChannel.WHATSAPP, "+911234567890",
                NotificationEventType.ORDER_PACKED, referenceId);
    }

    @Test
    void recurringWithinCooldown_skipsWithoutUserLookup() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        when(logRepository.existsByEventTypeAndReferenceIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(NotificationEventType.CART_ABANDONED), org.mockito.ArgumentMatchers.eq(cartId),
                org.mockito.ArgumentMatchers.any(java.time.Instant.class))).thenReturn(true);

        service.notifyRecurring(userId, NotificationEventType.CART_ABANDONED, cartId, java.time.Duration.ofHours(24));

        verifyNoInteractions(userRepository, dispatchQueue);
        verify(logRepository, never()).save(any());
    }

    @Test
    void recurring_pastCooldown_firesNewRowAndEnqueues() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setPhone("+911234567890");
        when(logRepository.existsByEventTypeAndReferenceIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(NotificationEventType.CART_ABANDONED), org.mockito.ArgumentMatchers.eq(cartId),
                org.mockito.ArgumentMatchers.any(java.time.Instant.class))).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(logRepository.save(any())).thenAnswer(invocation -> {
            NotificationLog saved = invocation.getArgument(0);
            saved.setId(logId);
            return saved;
        });

        service.notifyRecurring(userId, NotificationEventType.CART_ABANDONED, cartId, java.time.Duration.ofHours(24));

        ArgumentCaptor<NotificationLog> rowCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(rowCaptor.getValue().getEventType()).isEqualTo(NotificationEventType.CART_ABANDONED);
        verify(dispatchQueue).enqueue(logId, NotificationChannel.SMS, "+911234567890",
                NotificationEventType.CART_ABANDONED, cartId);
    }

    @Test
    void strictNotify_regressionPin_stillUsesPlainExistenceCheck() {
        // The shared-guard-body refactor must not leak the cooldown branch into notify():
        // a one-way moment checks the per-user existence guard (9-D fan-out scope),
        // never the windowed query.
        UUID userId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        when(logRepository.existsByEventTypeAndReferenceIdAndUserId(NotificationEventType.INVOICE_READY, referenceId, userId)).thenReturn(true);

        service.notify(userId, NotificationEventType.INVOICE_READY, referenceId);

        verify(logRepository).existsByEventTypeAndReferenceIdAndUserId(NotificationEventType.INVOICE_READY, referenceId, userId);
        verify(logRepository, org.mockito.Mockito.never()).existsByEventTypeAndReferenceIdAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(NotificationEventType.INVOICE_READY), org.mockito.ArgumentMatchers.eq(referenceId),
                org.mockito.ArgumentMatchers.any(java.time.Instant.class));
        verifyNoInteractions(userRepository, dispatchQueue);
    }
}
