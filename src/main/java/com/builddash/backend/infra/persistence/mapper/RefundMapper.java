package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.infra.persistence.entity.RefundEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RefundMapper {

    public RefundEntity toEntity(Refund domain) {
        if (domain == null) {
            return null;
        }
        RefundEntity entity = new RefundEntity();
        entity.setId(domain.id());
        entity.setReturnId(domain.returnId());
        entity.setPaymentTransactionId(domain.paymentTransactionId());
        entity.setAmount(domain.amount());
        entity.setStatus(domain.status());
        entity.setGatewayRefundId(domain.gatewayRefundId());

        Instant now = Instant.now();
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : now);
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : now);

        return entity;
    }

    public Refund toDomain(RefundEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Refund(
                entity.getId(),
                entity.getReturnId(),
                entity.getPaymentTransactionId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getGatewayRefundId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
