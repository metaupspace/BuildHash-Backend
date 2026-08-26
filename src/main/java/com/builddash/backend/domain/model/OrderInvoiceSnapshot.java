package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderInvoiceSnapshot(
        UUID orderId,
        String invoiceNumber,
        Instant orderPlacedAt,
        String customerPhone,
        String deliveryAddress,
        List<InvoiceLineItemSnapshot> lineItems,
        BigDecimal subTotal,
        BigDecimal totalTax,
        BigDecimal totalAmount
) {
    public record InvoiceLineItemSnapshot(
            UUID productId,
            String productName,
            String hsnCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {}
}
