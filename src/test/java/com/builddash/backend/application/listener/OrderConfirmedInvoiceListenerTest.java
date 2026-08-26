package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.OrderConfirmedEvent;
import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderConfirmedInvoiceListenerTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private OrderConfirmedInvoiceListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderConfirmedInvoiceListener(invoiceRepository);
    }

    @Test
    void onOrderConfirmed_newOrder_initializesPendingInvoice() {
        UUID orderId = UUID.randomUUID();
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        listener.onOrderConfirmed(new OrderConfirmedEvent(orderId));

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());

        Invoice saved = captor.getValue();
        assertThat(saved.orderId()).isEqualTo(orderId);
        assertThat(saved.status()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(saved.attemptCount()).isEqualTo(0);
        assertThat(saved.number()).isNull();
    }

    @Test
    void onOrderConfirmed_alreadyExistingInvoice_ignores() {
        UUID orderId = UUID.randomUUID();
        Invoice existing = new Invoice(UUID.randomUUID(), orderId, "INV-001", InvoiceStatus.READY, null, null, null, 1, null, null);
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        listener.onOrderConfirmed(new OrderConfirmedEvent(orderId));

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void onOrderConfirmed_nullEvent_ignores() {
        listener.onOrderConfirmed(null);
        verify(invoiceRepository, never()).save(any());
    }
}
