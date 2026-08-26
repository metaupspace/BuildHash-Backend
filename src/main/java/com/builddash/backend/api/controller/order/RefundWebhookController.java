package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.RefundWebhookRequest;
import com.builddash.backend.application.service.RefundWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/refund")
@RequiredArgsConstructor
public class RefundWebhookController {

    private final RefundWebhookService refundWebhookService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@Valid @RequestBody RefundWebhookRequest request) {
        refundWebhookService.handleWebhook(request.returnId(), request.gatewayRefundId(), request.status(), request.signature());
        return ResponseEntity.ok().build();
    }
}
