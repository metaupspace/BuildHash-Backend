package com.builddash.backend.api.dto.response;

import java.util.UUID;

public record ChatResponse(
        String intent,
        double confidence,
        boolean escalated,
        UUID ticketId
) {}
