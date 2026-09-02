package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that GST sequence allocation participating in a rolled-back transaction
 * rolls back the table row increment in PostgreSQL WAL, guaranteeing that the
 * subsequent committed allocation receives the exact uncommitted sequence number
 * with zero numbering gaps.
 */
class GstSequenceRollbackJpaIT extends AbstractIntegrationTest {

    @Autowired
    private GstSequenceService sequenceService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void sequenceAllocation_onTransactionRollback_restoresPreviousValAndAvoidsGaps() {
        Instant asOf = Instant.parse("2026-05-15T10:00:00Z"); // FY 2026-2027 ("INV-2627-")

        // Tx 1: Allocate #1 and commit
        String num1 = transactionTemplate.execute(tx -> sequenceService.nextNumber(GstSequenceType.INVOICE, asOf));
        assertThat(num1).matches("^INV-2627-\\d{6}$");

        long val1 = Long.parseLong(num1.substring("INV-2627-".length()));

        // Tx 2: Allocate #2 inside transaction that fails and rolls back
        assertThatThrownBy(() -> transactionTemplate.execute(tx -> {
            String num2 = sequenceService.nextNumber(GstSequenceType.INVOICE, asOf);
            assertThat(num2).isEqualTo(String.format("INV-2627-%06d", val1 + 1));
            throw new RuntimeException("Simulated transaction failure after sequence allocation");
        })).isInstanceOf(RuntimeException.class);

        // Tx 3: Allocate in a new transaction -> Must receive the rolled-back number (#2), proving zero gap!
        String num3 = transactionTemplate.execute(tx -> sequenceService.nextNumber(GstSequenceType.INVOICE, asOf));
        assertThat(num3).isEqualTo(String.format("INV-2627-%06d", val1 + 1));

        // Tx 4: Allocate next number -> Receives #3 monotonically
        String num4 = transactionTemplate.execute(tx -> sequenceService.nextNumber(GstSequenceType.INVOICE, asOf));
        assertThat(num4).isEqualTo(String.format("INV-2627-%06d", val1 + 2));
    }

    @Test
    void creditNoteSequence_onTransactionRollback_alsoRestoresWithoutGaps() {
        Instant asOf = Instant.parse("2026-05-15T10:00:00Z");

        // Tx 1: Allocate Credit Note #1 and commit
        String crn1 = transactionTemplate.execute(tx -> sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE, asOf));
        assertThat(crn1).matches("^CRN-2627-\\d{6}$");
        long crnVal1 = Long.parseLong(crn1.substring("CRN-2627-".length()));

        // Tx 2: Allocate Credit Note #2 and rollback
        assertThatThrownBy(() -> transactionTemplate.execute(tx -> {
            sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE, asOf);
            throw new IllegalStateException("Abort Credit Note transaction");
        })).isInstanceOf(IllegalStateException.class);

        // Tx 3: Allocate Credit Note #2 in next commit
        String crn3 = transactionTemplate.execute(tx -> sequenceService.nextNumber(GstSequenceType.CREDIT_NOTE, asOf));
        assertThat(crn3).isEqualTo(String.format("CRN-2627-%06d", crnVal1 + 1));
    }
}
