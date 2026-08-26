package com.builddash.backend.infra.gateway;

import com.builddash.backend.application.event.PaymentWebhookEvent;
import com.builddash.backend.application.event.RefundWebhookEvent;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.RefundReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DummyPaymentGatewayAdapterTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DummyPaymentGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DummyPaymentGatewayAdapter(eventPublisher);
    }

    @Test
    void initiate_normalAmount_returnsReferenceAndPublishesSuccessEvent() {
        UUID orderId = UUID.randomUUID();
        PaymentReference ref = adapter.initiate(orderId, new BigDecimal("100.00"));

        assertThat(ref).isNotNull();
        assertThat(ref.transactionId()).startsWith("dummy_tx_");
        assertThat(ref.paymentUrl()).contains(ref.transactionId());

        ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(eventPublisher, timeout(3000)).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void initiate_amount9999_throwsSimulatedTimeout() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> adapter.initiate(orderId, new BigDecimal("9999")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated gateway connection timeout");
    }

    @Test
    void refund_normalAmount_returnsRefundReferenceAndPublishesSuccessEvent() {
        RefundReference ref = adapter.refund("tx_123", new BigDecimal("50.00"));

        assertThat(ref).isNotNull();
        assertThat(ref.gatewayRefundId()).startsWith("dummy_ref_");
        assertThat(ref.status()).isEqualTo("PENDING");

        ArgumentCaptor<RefundWebhookEvent> captor = ArgumentCaptor.forClass(RefundWebhookEvent.class);
        verify(eventPublisher, timeout(3000)).publishEvent(captor.capture());
        assertThat(captor.getValue().gatewayRefundId()).isEqualTo(ref.gatewayRefundId());
        assertThat(captor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void refund_amount9998_publishesFailedEvent() {
        RefundReference ref = adapter.refund("tx_123", new BigDecimal("9998"));

        assertThat(ref).isNotNull();
        assertThat(ref.gatewayRefundId()).startsWith("dummy_ref_");

        ArgumentCaptor<RefundWebhookEvent> captor = ArgumentCaptor.forClass(RefundWebhookEvent.class);
        verify(eventPublisher, timeout(3000)).publishEvent(captor.capture());
        assertThat(captor.getValue().gatewayRefundId()).isEqualTo(ref.gatewayRefundId());
        assertThat(captor.getValue().status()).isEqualTo("FAILED");
    }

    @Test
    void refund_amount9999_throwsSimulatedTimeout() {
        assertThatThrownBy(() -> adapter.refund("tx_123", new BigDecimal("9999")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated gateway connection timeout");
    }
}
