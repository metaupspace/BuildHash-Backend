package com.builddash.backend.application.event;

import java.util.UUID;

/** Approval decided (approved or rejected) — notifies the order placer (9-D). */
public record ApprovalDecidedEvent(
        UUID orderId,
        UUID requestId,
        boolean approved,
        UUID placerUserId
) {
}
