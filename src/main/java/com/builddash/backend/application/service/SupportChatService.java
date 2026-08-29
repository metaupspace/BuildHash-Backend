package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.SupportTicket;

import java.util.UUID;

/**
 * The chatbot surface (PLAN_PHASE7 5(g)): classify, then escalate to a human with context.
 */
public interface SupportChatService {

    ChatOutcome chat(UUID userId, String message);

    record ChatOutcome(String intent, double confidence, boolean escalated, SupportTicket ticket) {
    }
}
