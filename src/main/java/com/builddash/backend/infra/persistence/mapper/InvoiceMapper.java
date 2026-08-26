package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.infra.persistence.entity.InvoiceEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InvoiceMapper {

    public InvoiceEntity toEntity(Invoice domain) {
        if (domain == null) {
            return null;
        }

        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(domain.id());
        entity.setOrderId(domain.orderId());
        entity.setNumber(domain.number());
        entity.setStatus(domain.status());
        entity.setStorageKey(domain.storageKey());
        entity.setContentType(domain.contentType());
        entity.setGeneratedAt(domain.generatedAt());
        entity.setAttemptCount(domain.attemptCount());

        Instant now = Instant.now();
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : now);
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : now);

        return entity;
    }

    public Invoice toDomain(InvoiceEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Invoice(
                entity.getId(),
                entity.getOrderId(),
                entity.getNumber(),
                entity.getStatus(),
                entity.getStorageKey(),
                entity.getContentType(),
                entity.getGeneratedAt(),
                entity.getAttemptCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
