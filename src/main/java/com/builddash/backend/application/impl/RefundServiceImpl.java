package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.RefundReference;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.service.ReturnRefundCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final ReturnRepository returnRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final RefundRepository refundRepository;

    @Override
    @Transactional
    public Refund initiateRefund(UUID returnId) {
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));

        BigDecimal amount = ReturnRefundCalculator.calculateTotalRefund(returnObj.lineItems());

        Payment payment = paymentRepository.findLatestByOrderId(returnObj.orderId())
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "No payment found for order: " + returnObj.orderId()));

        String transactionId = payment.transactionId();

        RefundReference ref = paymentGateway.refund(transactionId, amount);

        Return initiated = returnObj.initiateRefund();
        returnRepository.save(initiated);

        Refund refund = new Refund(
                UUID.randomUUID(),
                returnId,
                transactionId,
                amount,
                RefundStatus.PENDING,
                ref != null ? ref.gatewayRefundId() : null,
                Instant.now(),
                Instant.now()
        );

        log.info("Initiated refund {} for return {} with amount {}", refund.id(), returnId, amount);
        return refundRepository.save(refund);
    }
}
