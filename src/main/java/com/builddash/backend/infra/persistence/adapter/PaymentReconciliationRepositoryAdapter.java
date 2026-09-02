package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.PaymentReconciliationStatus;
import com.builddash.backend.domain.enums.PaymentReconciliationType;
import com.builddash.backend.domain.model.PaymentReconciliation;
import com.builddash.backend.domain.port.PaymentReconciliationRepository;
import com.builddash.backend.infra.persistence.entity.PaymentReconciliationEntity;
import com.builddash.backend.infra.persistence.repository.PaymentReconciliationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentReconciliationRepositoryAdapter implements PaymentReconciliationRepository {

    private final PaymentReconciliationJpaRepository jpaRepository;

    @Override
    public PaymentReconciliation save(PaymentReconciliation domain) {
        PaymentReconciliationEntity entity = jpaRepository.findById(domain.id())
                .orElseGet(() -> {
                    PaymentReconciliationEntity e = new PaymentReconciliationEntity();
                    e.setId(domain.id());
                    return e;
                });
        entity.setOrderId(domain.orderId());
        entity.setPaymentId(domain.paymentId());
        entity.setTransactionId(domain.transactionId());
        entity.setAmount(domain.amount());
        entity.setReconciliationType(domain.reconciliationType());
        entity.setStatus(domain.status());
        entity.setNotes(domain.notes());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<PaymentReconciliation> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<PaymentReconciliation> findByOrderIdAndType(UUID orderId, PaymentReconciliationType type) {
        return jpaRepository.findByOrderIdAndReconciliationType(orderId, type).map(this::toDomain);
    }

    @Override
    public List<PaymentReconciliation> findByStatus(PaymentReconciliationStatus status) {
        return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    private PaymentReconciliation toDomain(PaymentReconciliationEntity entity) {
        return new PaymentReconciliation(
                entity.getId(),
                entity.getOrderId(),
                entity.getPaymentId(),
                entity.getTransactionId(),
                entity.getAmount(),
                entity.getReconciliationType(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : Instant.now()
        );
    }
}
