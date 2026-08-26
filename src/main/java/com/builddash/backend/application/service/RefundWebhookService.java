package com.builddash.backend.application.service;

import java.util.UUID;

public interface RefundWebhookService {
    void handleWebhook(UUID returnId, String gatewayRefundId, String status, String signature);
}
