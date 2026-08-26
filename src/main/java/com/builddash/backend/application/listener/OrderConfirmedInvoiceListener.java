package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.OrderConfirmedEvent;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedInvoiceListener {

    private final InvoiceRepository invoiceRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        if (invoiceRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Invoice already initialized for order {}, ignoring event", event.orderId());
            return;
        }

        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                event.orderId(),
                null,
                InvoiceStatus.PENDING,
                null,
                "application/pdf",
                null,
                0,
                Instant.now(),
                Instant.now()
        );

        invoiceRepository.save(invoice);
        log.info("Initialized PENDING invoice {} for confirmed order {}", invoice.id(), event.orderId());
    }
}
