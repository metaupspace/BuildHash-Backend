package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.infra.persistence.entity.ReturnEntity;
import com.builddash.backend.infra.persistence.entity.ReturnLineItemEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReturnMapper {

    public ReturnEntity toEntity(Return domain) {
        if (domain == null) {
            return null;
        }

        ReturnEntity entity = new ReturnEntity();
        entity.setId(domain.id());
        entity.setOrderId(domain.orderId());
        entity.setUserId(domain.userId());
        entity.setStatus(domain.status());
        entity.setReason(domain.reason());
        entity.setPhotoKeys(domain.photoKeys() != null ? new ArrayList<>(domain.photoKeys()) : new ArrayList<>());

        Instant now = Instant.now();
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : now);
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : now);

        if (domain.lineItems() != null) {
            domain.lineItems().forEach(item -> {
                ReturnLineItemEntity lineEntity = new ReturnLineItemEntity();
                lineEntity.setId(item.id());
                lineEntity.setProductId(item.productId());
                lineEntity.setQuantityRequested(item.quantityRequested());
                lineEntity.setRefundAmount(item.refundAmount());
                lineEntity.setCreatedAt(entity.getCreatedAt());
                lineEntity.setUpdatedAt(entity.getUpdatedAt());
                entity.addLineItem(lineEntity);
            });
        }

        return entity;
    }

    public Return toDomain(ReturnEntity entity) {
        if (entity == null) {
            return null;
        }

        List<ReturnLineItem> items = entity.getLineItems() != null
                ? entity.getLineItems().stream()
                .map(this::toLineItemDomain)
                .toList()
                : List.of();

        List<String> photoKeys = entity.getPhotoKeys() != null
                ? List.copyOf(entity.getPhotoKeys())
                : List.of();

        return new Return(
                entity.getId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getReason(),
                photoKeys,
                items,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ReturnLineItem toLineItemDomain(ReturnLineItemEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReturnLineItem(
                entity.getId(),
                entity.getReturnEntity() != null ? entity.getReturnEntity().getId() : null,
                entity.getProductId(),
                entity.getQuantityRequested(),
                entity.getRefundAmount()
        );
    }
}
