package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.OrderLineItemResponse;
import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.domain.model.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderDtoMapper {

    public OrderResponse toResponse(OrderResult result) {
        return toResponse(result.order(), result.paymentUrl());
    }

    public OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    private OrderResponse toResponse(Order order, String paymentUrl) {
        List<OrderLineItemResponse> items = order.lineItems().stream()
                .map(item -> new OrderLineItemResponse(item.productId(), item.quantity(), item.unitPrice(), item.taxAmount(), item.lineTotal()))
                .toList();

        return new OrderResponse(
                order.id(),
                order.status().name(),
                order.totalAmount(),
                paymentUrl,
                order.placedAt(),
                order.driverId(),
                order.driverPhone(),
                items
        );
    }
}
