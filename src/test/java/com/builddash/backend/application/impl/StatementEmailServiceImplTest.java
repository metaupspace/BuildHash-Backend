package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.StatementEmailService;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.EmailSender;
import com.builddash.backend.domain.port.EmailSender.EmailRequest;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.infra.config.StatementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementEmailServiceImplTest {

    @Mock
    private StatementRepository statementRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private EmailSender emailSender;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Spy
    private StatementProperties properties = new StatementProperties();

    @InjectMocks
    private StatementEmailServiceImpl emailService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID statementId = UUID.randomUUID();

    private final java.util.concurrent.atomic.AtomicReference<Statement> stored = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            Consumer<?> consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        // Stateful: markEmail's locked re-read sees the statement the test seeded.
        lenient().when(statementRepository.findByIdForUpdate(statementId))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        lenient().when(statementRepository.save(any())).thenAnswer(inv -> {
            Statement saved = inv.getArgument(0);
            stored.set(saved);
            return saved;
        });
    }

    private Statement ready(long pdfBytes, long xlsxBytes) {
        return new Statement(statementId, companyId,
                Instant.parse("2026-08-31T18:30:00Z"), Instant.parse("2026-09-30T18:30:00Z"),
                "202609", StatementStatus.READY, 1, "ST-202609-0001",
                "statements/" + companyId + "/" + statementId + "/statement.pdf",
                "statements/" + companyId + "/" + statementId + "/statement.xlsx",
                pdfBytes, xlsxBytes, Instant.now(), 1, StatementEmailStatus.NONE, null, 0,
                1, new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("118.00"), List.of(), null, null);
    }

    @Test
    void deliver_sendsStoredArtifactsAsAttachments_marksSent() {
        Statement statement = ready(100, 200);
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        stored.set(statement);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company("accounts@acme.example")));
        when(objectStorage.get(statement.pdfStorageKey())).thenReturn(new byte[100]);
        when(objectStorage.get(statement.xlsxStorageKey())).thenReturn(new byte[200]);

        emailService.deliver(statementId);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailSender).send(captor.capture());
        EmailRequest request = captor.getValue();
        assertThat(request.to()).isEqualTo("accounts@acme.example");
        assertThat(request.subject()).isEqualTo("BuildDash statement 202609 — ST-202609-0001");
        assertThat(request.attachments()).hasSize(2);
        assertThat(request.attachments().get(0).filename()).isEqualTo("statement.pdf");
        assertThat(request.attachments().get(1).filename()).isEqualTo("statement.xlsx");
        // Retry reads STORED artifacts — nothing re-renders.
        verify(objectStorage).get(statement.pdfStorageKey());
        verify(objectStorage).get(statement.xlsxStorageKey());

        verify(statementRepository).save(org.mockito.ArgumentMatchers.argThat(
                s -> s != null && s.emailStatus() == StatementEmailStatus.SENT
                        && s.emailAttemptCount() == 1));
    }

    @Test
    void deliver_noStatementEmail_marksSkipped_neverSends() {
        Statement statement = ready(10, 10);
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        stored.set(statement);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company(null)));

        emailService.deliver(statementId);

        verify(emailSender, never()).send(any());
        verify(objectStorage, never()).get(anyString());
        verify(statementRepository).save(org.mockito.ArgumentMatchers.argThat(
                s -> s != null && s.emailStatus() == StatementEmailStatus.SKIPPED));
    }

    @Test
    void deliver_oversizedAttachments_rejectedBeforeLoadingAnyByte() {
        Statement statement = ready(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2);
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        stored.set(statement);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company("a@b.c")));

        assertThatThrownBy(() -> emailService.deliver(statementId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed configured limit");

        verify(objectStorage, never()).get(anyString()); // size gate preceded any load
        verify(emailSender, never()).send(any());
        verify(statementRepository).save(org.mockito.ArgumentMatchers.argThat(
                s -> s != null && s.emailStatus() == StatementEmailStatus.FAILED
                        && s.emailAttemptCount() == 1));
    }

    @Test
    void deliver_senderFailure_marksFailed_statementStaysReady() {
        Statement statement = ready(10, 10);
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        stored.set(statement);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company("a@b.c")));
        when(objectStorage.get(statement.pdfStorageKey())).thenReturn(new byte[10]);
        when(objectStorage.get(statement.xlsxStorageKey())).thenReturn(new byte[10]);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider timeout"))
                .when(emailSender).send(any());

        assertThatThrownBy(() -> emailService.deliver(statementId))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<Statement> captor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(StatementStatus.READY); // untouched
        assertThat(captor.getValue().emailStatus()).isEqualTo(StatementEmailStatus.FAILED);
        assertThat(captor.getValue().emailAttemptCount()).isEqualTo(1);
    }

    @Test
    void deliver_oversizedAttachments_marksFailedWithoutCallingSender() {
        Statement statement = ready(6_000_000L, 5_000_000L); // 11MB > 10MB cap
        when(statementRepository.findById(statementId)).thenReturn(Optional.of(statement));
        stored.set(statement);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company("a@b.c")));

        assertThatThrownBy(() -> emailService.deliver(statementId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed configured limit");

        verify(emailSender, never()).send(any());
        verify(objectStorage, never()).get(anyString());
        ArgumentCaptor<Statement> captor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).save(captor.capture());
        assertThat(captor.getValue().emailStatus()).isEqualTo(StatementEmailStatus.FAILED);
    }

    private Company company(String email) {
        return new Company(companyId, "Acme", null, email, "Asia/Kolkata",
                com.builddash.backend.domain.enums.CompanyStatus.ACTIVE, null, null);
    }
}
