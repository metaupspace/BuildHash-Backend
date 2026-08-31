package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.InvalidReturnStateException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Three-phase shape (8.1-C, OrderServiceImpl gateway discipline): a durable PENDING
     * claim commits first, the gateway call runs outside every transaction, and a second
     * short transaction finalizes. A crash between gateway success and finalization
     * leaves the claim visible (PENDING, no gateway id) for reconciliation — the current
     * PaymentGateway abstraction cannot resolve that ambiguity itself, so the evidence
     * stays; it is never silently discarded.
     */
    @Override
    public Refund initiateRefund(UUID returnId) {
        ClaimContext claim = transactionTemplate.execute(status -> claimForRefund(returnId));

        final RefundReference ref;
        try {
            ref = paymentGateway.refund(claim.transactionId(), claim.amount());
        } catch (RuntimeException e) {
            // Failure records the claim FAILED and leaves the Return at QC — retryable,
            // with no new domain transition needed. The claim row is the audit trail.
            transactionTemplate.executeWithoutResult(status ->
                    refundRepository.save(claim.refund().markFailed(null)));
            throw e;
        }

        return transactionTemplate.execute(status -> finalizeClaim(claim, ref));
    }

    private ClaimContext claimForRefund(UUID returnId) {
        Return returnObj = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));

        if (returnObj.status() != ReturnStatus.QC) {
            throw new InvalidReturnStateException(returnObj.status().name(), ReturnStatus.REFUND_INITIATED.name());
        }
        // One in-flight claim per return, serialized by the row lock: a concurrent second
        // initiator sees the first's committed claim here and stops — before any gateway
        // call. FAILED claims do not block: failure keeps the return retryable and a
        // retry writes a fresh claim.
        refundRepository.findAllByReturnId(returnId).stream()
                .filter(existing -> existing.status() != RefundStatus.FAILED)
                .findAny()
                .ifPresent(existing -> {
                    throw new InvalidReturnStateException(returnObj.status().name(), ReturnStatus.REFUND_INITIATED.name());
                });

        BigDecimal amount = ReturnRefundCalculator.calculateTotalRefund(returnObj.lineItems());
        Payment payment = paymentRepository.findLatestByOrderId(returnObj.orderId())
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "No payment found for order: " + returnObj.orderId()));

        Refund claim = refundRepository.save(new Refund(
                UUID.randomUUID(), returnId, payment.transactionId(), amount,
                RefundStatus.PENDING, null, Instant.now(), Instant.now()));
        log.info("Durable refund claim {} created for return {} (amount {})", claim.id(), returnId, amount);
        return new ClaimContext(payment.transactionId(), amount, claim);
    }

    private Refund finalizeClaim(ClaimContext claim, RefundReference ref) {
        UUID returnId = claim.refund().returnId();
        Return locked = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        returnRepository.save(locked.initiateRefund());
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, ReturnStatus.QC, ReturnStatus.REFUND_INITIATED));

        // PENDING stays: the terminal transition belongs to the refund webhook. A null
        // gateway id stays null (ambiguous-outcome evidence is preserved, not invented).
        Refund finalized = new Refund(
                claim.refund().id(), returnId, claim.refund().paymentTransactionId(),
                claim.refund().amount(), RefundStatus.PENDING, ref.gatewayRefundId(),
                claim.refund().createdAt(), Instant.now());
        log.info("Refund {} finalized for return {} with gateway id {}", finalized.id(), returnId, ref.gatewayRefundId());
        return refundRepository.save(finalized);
    }

    private record ClaimContext(String transactionId, BigDecimal amount, Refund refund) {}
}
