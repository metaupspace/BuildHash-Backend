package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentWebhookService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliverySlotService deliverySlotService;

    @Override
    @Transactional
    public void handleWebhook(UUID orderId, String status, String signature) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.status() != OrderStatus.PAYMENT_PENDING) {
            log.info("Order {} is already {}, ignoring webhook", orderId, order.status());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(status)) {
            Order confirmed = order.confirm();
            orderRepository.save(confirmed);
            updatePaymentStatus(orderId, PaymentStatus.SUCCESS);
            deliverySlotService.releaseLock(confirmed.deliverySlotLockId(), confirmed.userId());
            log.info("Order {} confirmed successfully", orderId);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            updatePaymentStatus(orderId, PaymentStatus.FAILED);
            log.info("Payment failed for order {}", orderId);
        } else {
            log.warn("Unknown payment status {} for order {}", status, orderId);
        }
    }

    private void updatePaymentStatus(UUID orderId, PaymentStatus status) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            com.builddash.backend.domain.model.Payment updated = status == PaymentStatus.SUCCESS ? 
                payment.markSuccess(payment.transactionId()) : payment.markFailed(payment.transactionId());
            paymentRepository.save(updated);
        });
    }
}
