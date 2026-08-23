package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.PaymentWebhookRequest;
import com.builddash.backend.application.service.PaymentWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@Valid @RequestBody PaymentWebhookRequest request) {
        paymentWebhookService.handleWebhook(request.orderId(), request.status(), request.signature());
        return ResponseEntity.ok().build();
    }
}
