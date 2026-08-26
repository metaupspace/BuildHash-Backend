package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.InvoiceQueryService;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceQueryServiceImpl implements InvoiceQueryService {

    private static final Duration SIGNED_URL_TTL = Duration.ofHours(1);

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectStorage objectStorage;

    @Override
    @Transactional(readOnly = true)
    public InvoiceQueryResult getInvoice(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId));

        if (!order.userId().equals(userId)) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId);
        }

        Optional<Invoice> invoiceOpt = invoiceRepository.findByOrderId(orderId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            if (invoice.status() == InvoiceStatus.READY && invoice.storageKey() != null) {
                String signedUrl = objectStorage.signedUrl(invoice.storageKey(), SIGNED_URL_TTL);
                Instant expiresAt = Instant.now().plus(SIGNED_URL_TTL);
                return new InvoiceQueryResult(InvoiceStatus.READY, invoice.number(), signedUrl, expiresAt);
            }
        }

        return new InvoiceQueryResult(InvoiceStatus.GENERATING, null, null, null);
    }
}
