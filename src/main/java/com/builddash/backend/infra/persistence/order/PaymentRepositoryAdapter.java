package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = jpaRepository.findById(payment.id()).orElse(new PaymentEntity());
        entity.setId(payment.id());
        
        OrderEntity orderRef = new OrderEntity();
        orderRef.setId(payment.orderId());
        entity.setOrder(orderRef);
        
        entity.setTransactionId(payment.transactionId());
        entity.setAmount(payment.amount());
        entity.setStatus(payment.status());
        entity.setPaymentUrl(payment.paymentUrl());
        
        PaymentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findLatestByOrderId(UUID orderId) {
        return jpaRepository.findFirstByOrderIdOrderByCreatedAtDescIdDesc(orderId).map(this::toDomain);
    }

    private Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getOrder().getId(),
                entity.getTransactionId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getPaymentUrl()
        );
    }

    @Override
    public java.util.List<Payment> findAllByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).stream()
                .map(this::toDomain)
                .toList();
    }
}
