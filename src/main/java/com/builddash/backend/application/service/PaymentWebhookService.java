package com.builddash.backend.application.service;

import java.util.UUID;

public interface PaymentWebhookService {
    void handleWebhook(UUID orderId, String status, String signature);
}
