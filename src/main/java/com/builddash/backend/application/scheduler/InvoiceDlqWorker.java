package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.InvoiceGenerationService;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.infra.config.InvoiceQueueConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceDlqWorker {

    public static final int MAX_INITIAL_ATTEMPTS = 3;
    public static final int MAX_TOTAL_ATTEMPTS = 6;
    private static final Duration STALENESS_THRESHOLD = Duration.ofMinutes(15);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceGenerationService invoiceGenerationService;

    @RabbitListener(queues = InvoiceQueueConfig.DLQ_NAME)
    public void onDlqMessage(String invoiceIdStr) {
        if (invoiceIdStr == null || invoiceIdStr.isBlank()) {
            return;
        }
        try {
            UUID invoiceId = UUID.fromString(invoiceIdStr.trim());
            log.info("Received DLQ retry message for invoice {}", invoiceId);
            invoiceGenerationService.processInvoice(invoiceId);
        } catch (Exception e) {
            log.error("Failed processing DLQ message for invoice {}: {}", invoiceIdStr, e.getMessage());
        }
    }

    @Scheduled(cron = "${invoice.dlq.sweep.cron:0 0 */2 * * *}")
    public void runTwoHourDlqSweep() {
        Instant cutoff = Instant.now().minus(STALENESS_THRESHOLD);
        List<Invoice> claimable = invoiceRepository.findDlqClaimableInvoices(MAX_INITIAL_ATTEMPTS, MAX_TOTAL_ATTEMPTS, cutoff);
        log.info("Running 2-hour DLQ sweep for {} invoices", claimable.size());

        for (Invoice invoice : claimable) {
            try {
                invoiceGenerationService.processInvoice(invoice.id());
            } catch (Exception e) {
                log.error("2-hour DLQ sweep retry failed for invoice {}: {}", invoice.id(), e.getMessage());
            }
        }
    }
}
