package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.GatewayRejectedException;
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
            ref = paymentGateway.refund(claim.transactionId(), claim.amount(), returnId);
        } catch (GatewayRejectedException e) {
            // Definitive rejection: the gateway is certain nothing moved. Safe to mark
            // the claim FAILED and leave the Return at QC — retryable.
            transactionTemplate.executeWithoutResult(status ->
                    refundRepository.save(claim.refund().markFailed(null)));
            throw e;
        } catch (RuntimeException e) {
            // Ambiguous/unclassified failure (H1.6): money may already have moved on the
            // gateway side. The claim stays PENDING exactly as claimForRefund committed
            // it — never mark FAILED merely because the call didn't return cleanly, since
            // FAILED is retry-eligible and a retry here could issue a second real refund.
            log.warn("Ambiguous refund gateway outcome for claim {} (return {}); leaving claim PENDING for reconciliation: {}",
                    claim.refund().id(), returnId, e.getMessage());
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
        // Canonical lock order (RETURN -> REFUND), identical to RefundWebhookServiceImpl:
        // a concurrent webhook delivery for this same claim serializes against this call
        // instead of racing it.
        Return locked = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        Refund lockedRefund = refundRepository.findByIdForUpdate(claim.refund().id())
                .orElseThrow(() -> new NotFoundException("REFUND_NOT_FOUND", "Refund not found: " + claim.refund().id()));

        if (lockedRefund.status() == RefundStatus.SUCCESS || lockedRefund.status() == RefundStatus.FAILED) {
            // A concurrent webhook already reached a terminal state for this claim while
            // the gateway call was in flight. SUCCESS is monotonic: finalize must not
            // downgrade it back to PENDING or erase the gateway id the webhook recorded.
            log.info("Refund {} already reached terminal status {} via a concurrent webhook; finalize is a no-op",
                    lockedRefund.id(), lockedRefund.status());
            return lockedRefund;
        }

        returnRepository.save(locked.initiateRefund());
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, ReturnStatus.QC, ReturnStatus.REFUND_INITIATED));

        // PENDING stays: the terminal transition belongs to the refund webhook. A null
        // gateway id stays null (ambiguous-outcome evidence is preserved, not invented).
        Refund finalized = new Refund(
                lockedRefund.id(), returnId, lockedRefund.paymentTransactionId(),
                lockedRefund.amount(), RefundStatus.PENDING, ref.gatewayRefundId(),
                lockedRefund.createdAt(), Instant.now());
        log.info("Refund {} finalized for return {} with gateway id {}", finalized.id(), returnId, ref.gatewayRefundId());
        return refundRepository.save(finalized);
    }

    private record ClaimContext(String transactionId, BigDecimal amount, Refund refund) {}
}
