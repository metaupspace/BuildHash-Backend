package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.StatementSequenceService;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.StatementAccountingRepository;
import com.builddash.backend.domain.port.StatementAccountingRepository.AccountingTotals;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.domain.port.StatementRenderer;
import com.builddash.backend.domain.port.StatementWorkbookWriter;
import com.builddash.backend.infra.config.StatementProperties;
import com.builddash.backend.infra.persistence.repository.CompanyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementGenerationServiceImplTest {

    @Mock
    private StatementRepository statementRepository;
    @Mock
    private StatementAccountingRepository accountingRepository;
    @Mock
    private StatementRenderer statementRenderer;
    @Mock
    private StatementWorkbookWriter workbookWriter;
    @Mock
    private StatementSequenceService sequenceService;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyJpaRepository companyJpaRepository;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Spy
    private StatementProperties properties = new StatementProperties();

    @InjectMocks
    private StatementGenerationServiceImpl generationService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID statementId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-08-31T18:30:00Z");
    private final Instant end = Instant.parse("2026-09-30T18:30:00Z");

    private final java.util.concurrent.atomic.AtomicReference<Statement> stored = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(inv -> {
            Consumer<?> consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        // Stateful stub: re-reads always see the latest SAVED statement (mirrors the DB).
        lenient().when(statementRepository.save(any())).thenAnswer(inv -> {
            Statement saved = inv.getArgument(0);
            stored.set(saved);
            return saved;
        });
        lenient().when(statementRepository.findByIdForUpdate(statementId))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        lenient().when(companyJpaRepository.findById(companyId))
                .thenReturn(Optional.of(companyEntity()));
    }

    private com.builddash.backend.infra.persistence.entity.CompanyEntity companyEntity() {
        var entity = new com.builddash.backend.infra.persistence.entity.CompanyEntity();
        entity.setId(companyId);
        entity.setName("Acme");
        entity.setGstNumber("27AAAPZ1234C1ZV");
        entity.setStatementEmail("a@b.c");
        entity.setBusinessTimezone("Asia/Kolkata");
        entity.setStatus(com.builddash.backend.domain.enums.CompanyStatus.ACTIVE);
        return entity;
    }

    private Statement generating(int attempts) {
        return new Statement(statementId, companyId, start, end, "202609",
                StatementStatus.PENDING, 1, null, null, null, null, null, null, attempts,
                StatementEmailStatus.NONE, null, 0, null, null, null, null, null, null,
                List.of(), Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600));
    }

    @Test
    void process_happyPath_rendersStoresFinalizesWithNumber() {
        Statement claimed = generating(0);
        stored.set(claimed);
        when(accountingRepository.aggregateTotals(companyId, start, end))
                .thenReturn(new AccountingTotals(1, new BigDecimal("118.00"), new BigDecimal("18.00"), 0, BigDecimal.ZERO));
        when(accountingRepository.findOrderRows(eq(companyId), eq(start), eq(end), any(), anyInt()))
                .thenReturn(List.of(new StatementOrderRow(UUID.randomUUID(), null, Instant.now(),
                        new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "READY")))
                .thenReturn(List.of());
        when(statementRenderer.render(any(), any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(workbookWriter.write(any(), any(), any(), any())).thenReturn(new byte[]{4, 5, 6});
        when(sequenceService.nextNumber(companyId, "202609")).thenReturn("ST-202609-0001");

        assertThat(generationService.process(statementId)).isTrue();

        verify(objectStorage).store(eq(contains(statementId, "/statement.pdf")), eq(new byte[]{1, 2, 3}), eq("application/pdf"));
        verify(objectStorage).store(eq(contains(statementId, "/statement.xlsx")), eq(new byte[]{4, 5, 6}), anyString());
        verify(sequenceService).nextNumber(companyId, "202609");

        ArgumentCaptor<Statement> captor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Statement ready = captor.getAllValues().stream()
                .filter(s -> s.status() == StatementStatus.READY).findFirst().orElseThrow();
        assertThat(ready.statementNumber()).isEqualTo("ST-202609-0001");
        assertThat(ready.grossTotal()).isEqualByComparingTo("118.00");
        assertThat(ready.taxTotal()).isEqualByComparingTo("18.00");
        assertThat(ready.netTotal()).isEqualByComparingTo("100.00");
        assertThat(ready.dueTotal()).isEqualByComparingTo("118.00");
        assertThat(ready.pdfSizeBytes()).isEqualTo(3L);
        assertThat(ready.xlsxSizeBytes()).isEqualTo(3L);
        assertThat(ready.discrepancies()).isEmpty();
    }

    @Test
    void process_invoiceNotReady_includedWithDiscrepancy() {
        Statement claimed = generating(0);
        stored.set(claimed);
        UUID orderId = UUID.randomUUID();
        when(accountingRepository.aggregateTotals(companyId, start, end))
                .thenReturn(new AccountingTotals(1, new BigDecimal("118.00"), new BigDecimal("18.00"), 0, BigDecimal.ZERO));
        when(accountingRepository.findOrderRows(eq(companyId), eq(start), eq(end), any(), anyInt()))
                .thenReturn(List.of(new StatementOrderRow(orderId, null, Instant.now(),
                        new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "GENERATING")))
                .thenReturn(List.of());
        when(statementRenderer.render(any(), any(), any())).thenReturn(new byte[]{1});
        when(workbookWriter.write(any(), any(), any(), any())).thenReturn(new byte[]{1});
        when(sequenceService.nextNumber(companyId, "202609")).thenReturn("ST-202609-0001");

        generationService.process(statementId);

        ArgumentCaptor<Statement> captor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Statement ready = captor.getAllValues().stream()
                .filter(s -> s.status() == StatementStatus.READY).findFirst().orElseThrow();
        // Order still counted in totals; the invoice gap is recorded, not omitted.
        assertThat(ready.orderCount()).isEqualTo(1);
        assertThat(ready.grossTotal()).isEqualByComparingTo("118.00");
        assertThat(ready.discrepancies()).hasSize(1);
        assertThat(ready.discrepancies().get(0).type())
                .isEqualTo(com.builddash.backend.domain.enums.StatementDiscrepancyType.INVOICE_NOT_READY);
        assertThat(ready.discrepancies().get(0).orderId()).isEqualTo(orderId);
    }

    @Test
    void process_crossCheckMismatch_abortsWithoutNumber() {
        Statement claimed = generating(0);
        stored.set(claimed);
        when(accountingRepository.aggregateTotals(companyId, start, end))
                .thenReturn(new AccountingTotals(2, new BigDecimal("236.00"), new BigDecimal("36.00"), 0, BigDecimal.ZERO));
        when(accountingRepository.findOrderRows(eq(companyId), eq(start), eq(end), any(), anyInt()))
                .thenReturn(List.of(new StatementOrderRow(UUID.randomUUID(), null, Instant.now(),
                        new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "READY")))
                .thenReturn(List.of());

        assertThat(generationService.process(statementId)).isFalse();

        verify(statementRenderer, never()).render(any(), any(), any());
        verify(sequenceService, never()).nextNumber(any(), any());
        verify(statementRepository, never()).save(org.mockito.ArgumentMatchers.argThat(argThatReady()));
    }

    @Test
    void process_renderFailure_atCapMovesToDlqRetry() {
        Statement claimed = generating(2); // next claim reaches the cap of 3
        stored.set(claimed);
        when(accountingRepository.aggregateTotals(companyId, start, end))
                .thenReturn(new AccountingTotals(1, new BigDecimal("118.00"), new BigDecimal("18.00"), 0, BigDecimal.ZERO));
        when(accountingRepository.findOrderRows(eq(companyId), eq(start), eq(end), any(), anyInt()))
                .thenReturn(List.of(new StatementOrderRow(UUID.randomUUID(), null, Instant.now(),
                        new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"), "READY")))
                .thenReturn(List.of());
        when(statementRenderer.render(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(generationService.process(statementId)).isFalse();

        ArgumentCaptor<Statement> captor = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(s -> s.status() == StatementStatus.DLQ_RETRY);
        verify(sequenceService, never()).nextNumber(any(), any()); // no number burned
    }

    @Test
    void process_alreadyReady_skips() {
        Statement ready = generating(0).markReady("ST-202609-0001", "k1", "k2", 1, 1, 1,
                new BigDecimal("118.00"), new BigDecimal("18.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, new BigDecimal("118.00"), List.of());
        stored.set(ready);

        assertThat(generationService.process(statementId)).isFalse();
        verify(statementRenderer, never()).render(any(), any(), any());
    }

    private static org.mockito.ArgumentMatcher<Statement> argThatReady() {
        return s -> s.status() == StatementStatus.READY;
    }

    private String contains(UUID id, String suffix) {
        return "statements/" + companyId + "/" + id + suffix;
    }
}
