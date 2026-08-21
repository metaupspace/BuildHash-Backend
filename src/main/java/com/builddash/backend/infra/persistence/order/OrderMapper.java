package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity();
        entity.setId(domain.id());
        entity.setUserId(domain.userId());
        entity.setAddressId(domain.addressId());
        entity.setSlotId(domain.slotId());
        entity.setSlotDate(domain.slotDate());
        entity.setDeliverySlotLockId(domain.deliverySlotLockId());
        entity.setTotalAmount(domain.totalAmount());
        entity.setStatus(domain.status());
        
        domain.lineItems().forEach(item -> {
            OrderLineItemEntity lineEntity = new OrderLineItemEntity();
            lineEntity.setId(item.id());
            lineEntity.setProductId(item.productId());
            lineEntity.setQuantity(item.quantity());
            lineEntity.setUnitPrice(item.unitPrice());
            lineEntity.setTaxAmount(item.taxAmount());
            entity.addLineItem(lineEntity);
        });
        
        return entity;
    }

    public Order toDomain(OrderEntity entity) {
        var items = entity.getLineItems().stream()
                .map(e -> new OrderLineItem(e.getId(), e.getProductId(), e.getQuantity(), e.getUnitPrice(), e.getTaxAmount()))
                .collect(Collectors.toList());
                
        return new Order(
                entity.getId(),
                entity.getUserId(),
                entity.getAddressId(),
                entity.getSlotId(),
                entity.getSlotDate(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getDeliverySlotLockId(),
                items
        );
    }
}
