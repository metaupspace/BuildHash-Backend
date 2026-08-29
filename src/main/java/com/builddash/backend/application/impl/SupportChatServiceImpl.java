package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.SupportChatService;
import com.builddash.backend.application.service.SupportTicketService;
import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.port.IntentClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * NAMED BEHAVIOR — ALWAYS ESCALATES IN PHASE 7, BY DESIGN: the stub classifier's fixed
 * confidence (0.2) always falls below ESCALATION_THRESHOLD, so every chat message creates
 * an ESCALATED ticket carrying the message as its first entry, regardless of content.
 * The classify() call and threshold comparison are real code paths — they always resolve
 * the same way given the stub (StubImageSearchProvider's "always zero matches" honesty).
 * A real classifier implementation changes outcomes without touching this class (OCP).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupportChatServiceImpl implements SupportChatService {

    static final double ESCALATION_THRESHOLD = 0.6;

    private final IntentClassifier intentClassifier;
    private final SupportTicketService supportTicketService;

    @Override
    @Transactional
    public ChatOutcome chat(UUID userId, String message) {
        IntentClassifier.Classification classification = intentClassifier.classify(message);

        boolean escalate = classification.confidence() < ESCALATION_THRESHOLD;
        SupportTicket ticket = supportTicketService.createTicket(
                userId, SupportTicketCategory.OTHER, subjectFrom(message), message);
        if (escalate) {
            ticket = supportTicketService.escalate(ticket.id());
        }
        return new ChatOutcome(classification.intent(), classification.confidence(), escalate, ticket);
    }

    private String subjectFrom(String message) {
        return message.length() <= 120 ? "Chat: " + message : "Chat: " + message.substring(0, 120);
    }
}
