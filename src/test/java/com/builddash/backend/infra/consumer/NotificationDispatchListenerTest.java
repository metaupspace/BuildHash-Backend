package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.PushNotificationSender;
import com.builddash.backend.domain.port.SmsNotificationSender;
import com.builddash.backend.domain.port.WhatsAppNotificationSender;
import com.builddash.backend.infra.messaging.NotificationDispatchMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchListenerTest {

    @Mock
    private PushNotificationSender pushSender;

    @Mock
    private SmsNotificationSender smsSender;

    @Mock
    private WhatsAppNotificationSender whatsAppSender;

    @Mock
    private NotificationLogRepository logRepository;

    private NotificationDispatchListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationDispatchListener(pushSender, smsSender, whatsAppSender, logRepository);
    }

    private NotificationDispatchMessage message(NotificationChannel channel) {
        return new NotificationDispatchMessage(UUID.randomUUID(), channel, "+911234567890",
                NotificationEventType.ORDER_PACKED, UUID.randomUUID());
    }

    @Test
    void whatsAppMessage_sendsViaWhatsAppSenderThenMarksSent() {
        NotificationDispatchMessage message = message(NotificationChannel.WHATSAPP);

        listener.onWhatsApp(message);

        verify(whatsAppSender).send(message.phone(), message.eventType(), message.referenceId());
        verify(logRepository).markSent(message.logId());
        verifyNoInteractions(pushSender, smsSender);
    }

    @Test
    void smsMessage_sendsViaSmsSenderThenMarksSent() {
        NotificationDispatchMessage message = message(NotificationChannel.SMS);

        listener.onSms(message);

        verify(smsSender).send(message.phone(), message.eventType(), message.referenceId());
        verify(logRepository).markSent(message.logId());
        verifyNoInteractions(whatsAppSender, pushSender);
    }

    @Test
    void pushMessage_sendsViaPushSenderThenMarksSent() {
        NotificationDispatchMessage message = message(NotificationChannel.PUSH);

        listener.onPush(message);

        verify(pushSender).send(message.phone(), message.eventType(), message.referenceId());
        verify(logRepository).markSent(message.logId());
        verifyNoInteractions(whatsAppSender, smsSender);
    }

    @Test
    void senderThrows_propagatesAndNeverMarksSent() {
        NotificationDispatchMessage message = message(NotificationChannel.WHATSAPP);
        doThrow(new IllegalStateException("gateway down")).when(whatsAppSender)
                .send(message.phone(), message.eventType(), message.referenceId());

        assertThatThrownBy(() -> listener.onWhatsApp(message))
                .isInstanceOf(IllegalStateException.class);

        verify(logRepository, never()).markSent(message.logId());
        verify(logRepository, never()).markFailed(message.logId());
    }

    @Test
    void deadLetteredMessage_marksFailed() {
        NotificationDispatchMessage message = message(NotificationChannel.WHATSAPP);

        listener.onDeadLetter(message);

        verify(logRepository).markFailed(message.logId());
        verifyNoInteractions(whatsAppSender, smsSender, pushSender);
    }
}
