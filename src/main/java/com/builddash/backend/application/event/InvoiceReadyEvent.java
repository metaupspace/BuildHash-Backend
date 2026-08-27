package com.builddash.backend.application.event;

import java.util.UUID;

public record InvoiceReadyEvent(
        UUID orderId
) {}
