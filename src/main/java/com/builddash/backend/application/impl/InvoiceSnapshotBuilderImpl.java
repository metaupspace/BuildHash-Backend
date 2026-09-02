package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.InvoiceSnapshotBuilder;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceSnapshotBuilderImpl implements InvoiceSnapshotBuilder {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final HsnGstRateRepository hsnGstRateRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderInvoiceSnapshot build(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId));

        String customerPhone = userRepository.findById(order.userId())
                .map(User::getPhone)
                .orElse("N/A");

        String deliveryAddress = addressRepository.findById(order.addressId())
                .map(this::formatAddress)
                .orElse("N/A");

        List<OrderInvoiceSnapshot.InvoiceLineItemSnapshot> lineItems = new ArrayList<>();
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal subTotal = BigDecimal.ZERO;

        for (OrderLineItem item : order.lineItems()) {
            Product product = productRepository.findById(item.productId()).orElse(null);
            String productName = product != null ? product.getName() : "Product " + item.productId();
            String hsnCode = product != null && product.getHsnCode() != null ? product.getHsnCode() : "N/A";

            // H4.4: Use immutable persisted line snapshot; fallback to HSN master only for legacy rows
            BigDecimal taxRate = item.taxRatePercent() != null
                    ? item.taxRatePercent()
                    : hsnGstRateRepository.findByHsnCode(hsnCode)
                            .map(HsnGstRate::getGstRatePercent)
                            .orElse(new BigDecimal("18.00"));

            BigDecimal lineSubTotal = item.lineTotal().subtract(item.taxAmount());
            subTotal = subTotal.add(lineSubTotal);
            totalTax = totalTax.add(item.taxAmount());

            lineItems.add(new OrderInvoiceSnapshot.InvoiceLineItemSnapshot(
                    item.productId(),
                    productName,
                    hsnCode,
                    item.quantity(),
                    item.unitPrice(),
                    taxRate,
                    item.taxAmount(),
                    item.lineTotal()
            ));
        }

        return new OrderInvoiceSnapshot(
                order.id(),
                null,
                order.placedAt(),
                customerPhone,
                deliveryAddress,
                lineItems,
                subTotal,
                totalTax,
                order.totalAmount()
        );
    }

    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.line1() != null) sb.append(address.line1());
        if (address.line2() != null) sb.append(", ").append(address.line2());
        if (address.city() != null) sb.append(", ").append(address.city());
        if (address.state() != null) sb.append(", ").append(address.state());
        if (address.zipCode() != null) sb.append(" - ").append(address.zipCode());
        return sb.toString();
    }
}
