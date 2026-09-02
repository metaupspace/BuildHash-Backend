package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.OrderConfirmedEvent;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentReconciliationService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.domain.port.PaymentReconciliationRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DeliverySlotService deliverySlotService;
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ReconciliationOutcome reconcileStalePendingPayment(UUID orderId) {
        Optional<Payment> paymentOpt = paymentRepository.findLatestByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            return ReconciliationOutcome.CANCEL_ELIGIBLE;
        }

        Payment payment = paymentOpt.get();
        if (payment.status() != PaymentStatus.PENDING) {
            return payment.status() == PaymentStatus.SUCCESS
                    ? ReconciliationOutcome.CONFIRMED
                    : ReconciliationOutcome.CANCEL_ELIGIBLE;
        }

        String transactionId = payment.transactionId();

        // Query upstream payment gateway outside database transaction
        Optional<PaymentStatus> gatewayStatus;
        try {
            gatewayStatus = paymentGateway.queryStatus(transactionId, orderId);
        } catch (Exception e) {
            log.warn("Failed to query payment gateway status for order {} tx {}: {}", orderId, transactionId, e.getMessage());
            return ReconciliationOutcome.AMBIGUOUS_HOLD;
        }

        if (gatewayStatus.isEmpty()) {
            log.info("Gateway status for order {} (tx {}) is ambiguous/unknown, leaving pending on hold",
                    orderId, transactionId);
            return ReconciliationOutcome.AMBIGUOUS_HOLD;
        }

        PaymentStatus resolvedStatus = gatewayStatus.get();
        if (resolvedStatus == PaymentStatus.SUCCESS) {
            final boolean[] confirmedHolder = new boolean[1];
            transactionTemplate.executeWithoutResult(status -> {
                Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
                if (order == null) return;

                if (order.status() == OrderStatus.PAYMENT_PENDING) {
                    Order confirmed = order.confirm();
                    orderRepository.save(confirmed);
                    String effectiveTxId = transactionId != null ? transactionId : payment.transactionId();
                    paymentRepository.save(payment.markSuccess(effectiveTxId));

                    if (invoiceRepository.findByOrderId(orderId).isEmpty()) {
                        invoiceRepository.save(new Invoice(
                                UUID.randomUUID(), orderId, null, InvoiceStatus.PENDING,
                                null, "application/pdf", null, 0, Instant.now(), Instant.now()
                        ));
                    }

                    deliverySlotService.consumeLock(confirmed.deliverySlotLockId(), confirmed.userId());
                    eventPublisher.publishEvent(new OrderConfirmedEvent(orderId));
                    confirmedHolder[0] = true;
                    log.info("Reconciled order {} to CONFIRMED based on gateway SUCCESS", orderId);
                } else {
                    // Order already cancelled/terminal; record payment success
                    paymentRepository.save(payment.markSuccess(transactionId));
                }
            });
            return confirmedHolder[0] ? ReconciliationOutcome.CONFIRMED : ReconciliationOutcome.AMBIGUOUS_HOLD;
        } else if (resolvedStatus == PaymentStatus.FAILED) {
            transactionTemplate.executeWithoutResult(status -> {
                paymentRepository.save(payment.markFailed(transactionId));
            });
            log.info("Reconciled payment for order {} to FAILED based on gateway status", orderId);
            return ReconciliationOutcome.CANCEL_ELIGIBLE;
        } else if (resolvedStatus == PaymentStatus.PENDING) {
            log.info("Payment for order {} is still pending on gateway, holding", orderId);
            return ReconciliationOutcome.AMBIGUOUS_HOLD;
        }

        return ReconciliationOutcome.AMBIGUOUS_HOLD;
    }
}
