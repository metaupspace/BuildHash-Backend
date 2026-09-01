package com.builddash.backend.application.service;

import com.builddash.backend.infra.persistence.entity.StatementSequenceEntity;
import com.builddash.backend.infra.persistence.repository.StatementSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Statement number allocation (9-E): ST-YYYYMM-#### per company per period, separate
 * from gst_sequences. Row-level locked increment (GstSequenceService precedent);
 * callers allocate only inside the finalize transaction after both artifacts are
 * stored, so failed generations never consume a number. Not gapless by design.
 */
@Service
@RequiredArgsConstructor
public class StatementSequenceService {

    private final StatementSequenceJpaRepository sequenceJpaRepository;

    /** Must join an existing transaction (the statement finalize Tx2). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(UUID companyId, String periodKey) {
        StatementSequenceEntity sequence = sequenceJpaRepository.findForUpdate(companyId, periodKey)
                .orElseGet(() -> {
                    StatementSequenceEntity created = new StatementSequenceEntity(companyId, periodKey);
                    // Concurrent first-allocators for the same pair serialize on the PK;
                    // a loser hits the constraint and the claim transaction retries.
                    try {
                        return sequenceJpaRepository.saveAndFlush(created);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        return sequenceJpaRepository.findForUpdate(companyId, periodKey)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Statement sequence vanished for " + companyId + "/" + periodKey));
                    }
                });

        long nextVal = sequence.getCurrentVal() + 1;
        sequence.setCurrentVal(nextVal);
        sequence.setUpdatedAt(Instant.now());
        sequenceJpaRepository.save(sequence);
        return String.format("ST-%s-%04d", periodKey, nextVal);
    }
}
