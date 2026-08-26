package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.InvoiceGenerationService;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.infra.config.InvoiceQueueConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceGenerationScheduler {

    private static final int MAX_INITIAL_ATTEMPTS = 3;
    private static final Duration STALENESS_THRESHOLD = Duration.ofMinutes(15);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceGenerationService invoiceGenerationService;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${invoice.generation.scheduler.delay-ms:60000}")
    public void runGenerationCycle() {
        Instant cutoff = Instant.now().minus(STALENESS_THRESHOLD);
        List<Invoice> claimable = invoiceRepository.findSchedulerClaimableInvoices(MAX_INITIAL_ATTEMPTS, cutoff);

        for (Invoice invoice : claimable) {
            try {
                invoiceGenerationService.processInvoice(invoice.id());
            } catch (Exception e) {
                log.error("Failed to generate invoice {}: {}", invoice.id(), e.getMessage());
                invoiceRepository.findById(invoice.id()).ifPresent(updated -> {
                    if (updated.attemptCount() >= MAX_INITIAL_ATTEMPTS) {
                        Invoice dlqInvoice = updated.markDlqRetry();
                        invoiceRepository.save(dlqInvoice);
                        rabbitTemplate.convertAndSend(InvoiceQueueConfig.DLX_NAME, InvoiceQueueConfig.DLQ_NAME, invoice.id().toString());
                        log.warn("Invoice {} exceeded max initial attempts ({}), transitioned to DLQ_RETRY and published to DLQ",
                                invoice.id(), MAX_INITIAL_ATTEMPTS);
                    }
                });
            }
        }
    }
}
