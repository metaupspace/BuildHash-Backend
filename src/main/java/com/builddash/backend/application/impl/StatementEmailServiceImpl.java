package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.StatementEmailService;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.EmailSender;
import com.builddash.backend.domain.port.EmailSender.EmailRequest;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.infra.config.StatementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Statement email delivery (9-E) — fully separate from generation: READY never depends
 * on it. Attachments are read from stored artifacts (ObjectStorage.get), never
 * re-rendered, so an email can only ever deliver the exact persisted accounting
 * artifact. Oversized sends are rejected from the persisted size columns BEFORE any
 * artifact byte is loaded. Provider timeout-after-accept may duplicate delivery on
 * retry — accepted limitation (no webhooks, no outbox, locked).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementEmailServiceImpl implements StatementEmailService {

    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StatementRepository statementRepository;
    private final CompanyRepository companyRepository;
    private final ObjectStorage objectStorage;
    private final EmailSender emailSender;
    private final StatementProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public int sweep() {
        List<UUID> due = statementRepository.findReadyStatementsAwaitingEmail(
                properties.getEmail().getMaxAttempts(), properties.getEmail().getSweepBatchLimit());
        int sent = 0;
        for (UUID id : due) {
            try {
                deliver(id);
                sent++;
            } catch (Exception e) {
                log.warn("Statement email failed for {}: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    @Override
    public void deliver(UUID statementId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new IllegalArgumentException("Statement not found: " + statementId));
        if (statement.status() != com.builddash.backend.domain.enums.StatementStatus.READY) {
            return;
        }

        String recipient = companyRepository.findById(statement.companyId())
                .map(c -> c.statementEmail())
                .orElse(null);
        if (recipient == null || recipient.isBlank()) {
            transactionTemplate.executeWithoutResult(s -> statementRepository
                    .save(statementRepository.findById(statementId).orElseThrow().markEmailSkipped()));
            return;
        }

        try {
            long totalBytes = orZero(statement.pdfSizeBytes()) + orZero(statement.xlsxSizeBytes());
            if (totalBytes > properties.getEmail().getMaxAttachmentBytes()) {
                // Rejected before loading any artifact byte. Consumes an attempt so the
                // statement lands on terminal FAILED after the cap instead of looping forever.
                throw new IllegalStateException("Statement attachments exceed configured limit: "
                        + totalBytes + " > " + properties.getEmail().getMaxAttachmentBytes());
            }

            byte[] pdf = objectStorage.get(statement.pdfStorageKey());
            byte[] xlsx = objectStorage.get(statement.xlsxStorageKey());

            EmailRequest request = new EmailRequest(recipient,
                    "BuildDash statement " + statement.periodKey() + " — " + statement.statementNumber(),
                    "Attached are the monthly statement documents for period " + statement.periodKey() + ".",
                    List.of(new EmailSender.Attachment("statement.pdf", PDF_MEDIA_TYPE, pdf),
                            new EmailSender.Attachment("statement.xlsx", XLSX_MEDIA_TYPE, xlsx)));
            emailSender.send(request); // outside any transaction
        } catch (Exception e) {
            markEmail(statementId, false);
            throw e;
        }

        markEmail(statementId, true);
    }

    private void markEmail(UUID statementId, boolean success) {
        transactionTemplate.executeWithoutResult(status -> {
            Statement locked = statementRepository.findByIdForUpdate(statementId).orElse(null);
            if (locked == null) {
                return;
            }
            statementRepository.save(success ? locked.markEmailSent() : locked.markEmailFailed());
        });
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
