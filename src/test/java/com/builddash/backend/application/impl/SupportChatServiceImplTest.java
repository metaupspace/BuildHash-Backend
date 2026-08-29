package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.SupportChatService;
import com.builddash.backend.application.service.SupportTicketService;
import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketStatus;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.port.IntentClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceImplTest {

    @Mock
    private IntentClassifier intentClassifier;

    @Mock
    private SupportTicketService supportTicketService;

    private SupportChatServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SupportChatServiceImpl(intentClassifier, supportTicketService);
    }

    private SupportTicket ticket(SupportTicketStatus status) {
        return new SupportTicket(UUID.randomUUID(), userId, SupportTicketCategory.OTHER, status,
                "Chat: hello", Instant.now().plusSeconds(3600), Instant.now(), Instant.now());
    }

    @Test
    void subThresholdConfidence_createsEscalatedTicketWithMessageAsContext() {
        SupportTicket created = ticket(SupportTicketStatus.OPEN);
        when(intentClassifier.classify("hello")).thenReturn(new IntentClassifier.Classification("UNKNOWN", 0.2));
        when(supportTicketService.createTicket(any(), any(), any(), eq("hello"))).thenReturn(created);
        when(supportTicketService.escalate(created.id())).thenReturn(ticket(SupportTicketStatus.ESCALATED));

        SupportChatService.ChatOutcome outcome = service.chat(userId, "hello");

        assertThat(outcome.intent()).isEqualTo("UNKNOWN");
        assertThat(outcome.confidence()).isEqualTo(0.2);
        assertThat(outcome.escalated()).isTrue();
        assertThat(outcome.ticket().status()).isEqualTo(SupportTicketStatus.ESCALATED);
        verify(supportTicketService).createTicket(userId, SupportTicketCategory.OTHER, "Chat: hello", "hello");
        verify(supportTicketService).escalate(created.id());
    }

    @Test
    void aboveThresholdConfidence_leavesTicketOpen() {
        // Unreachable with the stub (always 0.2 < 0.6) but pins the branch a real
        // classifier will exercise — the threshold comparison is real code, not decoration.
        when(intentClassifier.classify("hello")).thenReturn(new IntentClassifier.Classification("ORDER_STATUS", 0.9));
        when(supportTicketService.createTicket(any(), any(), any(), eq("hello"))).thenReturn(ticket(SupportTicketStatus.OPEN));

        SupportChatService.ChatOutcome outcome = service.chat(userId, "hello");

        assertThat(outcome.escalated()).isFalse();
        org.mockito.Mockito.verify(supportTicketService, org.mockito.Mockito.never()).escalate(any());
    }
}
