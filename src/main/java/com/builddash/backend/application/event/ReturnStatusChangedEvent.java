package com.builddash.backend.application.event;

import com.builddash.backend.domain.enums.ReturnStatus;

import java.util.UUID;

/**
 * One generic event for every Return transition (PLAN_PHASE7 Section 2.1): transition points span
 * three services (ReturnServiceImpl, RefundServiceImpl, RefundWebhookServiceImpl), so per-transition
 * event types would scatter records across publishers; listeners map {@code to} to a template.
 */
public record ReturnStatusChangedEvent(
        UUID returnId,
        ReturnStatus from,
        ReturnStatus to
) {}
