package com.builddash.backend.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMetrics {

    private final MeterRegistry registry;

    public ApplicationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAuthFailure(String reason) {
        registry.counter("app.auth.failures", "reason", reason).increment();
    }

    public void recordCheckout(String outcome) {
        registry.counter("app.checkout.attempts", "outcome", outcome).increment();
    }

    public void recordPaymentWebhook(String status) {
        registry.counter("app.payment.webhooks", "status", status).increment();
    }

    public void recordRefundOutcome(String outcome) {
        registry.counter("app.refund.outcomes", "outcome", outcome).increment();
    }

    public void recordInvoiceGeneration(String outcome) {
        registry.counter("app.invoice.generation", "outcome", outcome).increment();
    }

    public void recordJobExecution(String jobName, String outcome) {
        registry.counter("app.job.executions", "job", jobName, "outcome", outcome).increment();
    }

    public void recordOutboxFailure(String eventType) {
        registry.counter("app.outbox.failures", "event_type", eventType).increment();
    }

    public void recordExternalCall(String service, String outcome) {
        registry.counter("app.external.calls", "service", service, "outcome", outcome).increment();
    }
}
