package com.builddash.backend.application.service;

import com.builddash.backend.infra.persistence.entity.StatementSequenceEntity;
import com.builddash.backend.infra.persistence.repository.StatementSequenceJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementSequenceServiceTest {

    @Mock
    private StatementSequenceJpaRepository sequenceJpaRepository;

    @InjectMocks
    private StatementSequenceService sequenceService;

    private final UUID companyId = UUID.randomUUID();

    @Test
    void nextNumber_incrementsUnderRowLock_fourDigitPadding() {
        StatementSequenceEntity existing = new StatementSequenceEntity(companyId, "202609");
        existing.setCurrentVal(0);
        when(sequenceJpaRepository.findForUpdate(companyId, "202609")).thenReturn(Optional.of(existing));

        assertThat(sequenceService.nextNumber(companyId, "202609")).isEqualTo("ST-202609-0001");
        assertThat(existing.getCurrentVal()).isEqualTo(1L);
    }

    @Test
    void nextNumber_naturalOverflowPast9999() {
        StatementSequenceEntity existing = new StatementSequenceEntity(companyId, "202609");
        existing.setCurrentVal(9999);
        when(sequenceJpaRepository.findForUpdate(companyId, "202609")).thenReturn(Optional.of(existing));

        assertThat(sequenceService.nextNumber(companyId, "202609")).isEqualTo("ST-202609-10000");
    }

    @Test
    void nextNumber_separateCountersPerCompanyAndPeriod() {
        StatementSequenceEntity other = new StatementSequenceEntity(UUID.randomUUID(), "202610");
        other.setCurrentVal(41);
        when(sequenceJpaRepository.findForUpdate(any(), any())).thenReturn(Optional.of(other));

        // Allocated from the requested (company, period) counter only — a sibling
        // company's count never leaks into this one's format.
        assertThat(sequenceService.nextNumber(companyId, "202610")).isEqualTo("ST-202610-0042");
    }

    @Test
    void nextNumber_firstAllocationCreatesRow_recoveringFromConstraintRace() {
        StatementSequenceEntity created = new StatementSequenceEntity(companyId, "202609");
        when(sequenceJpaRepository.findForUpdate(companyId, "202609"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        when(sequenceJpaRepository.saveAndFlush(any())).thenAnswer(inv -> {
            StatementSequenceEntity e = inv.getArgument(0);
            if (e == created) {
                return e;
            }
            // First allocator loses the PK race.
            throw new org.springframework.dao.DataIntegrityViolationException("duplicate key");
        });

        assertThat(sequenceService.nextNumber(companyId, "202609")).isEqualTo("ST-202609-0001");
        verify(sequenceJpaRepository).save(any());
    }

    @Test
    void nextNumber_vanishedSequence_failsLoudly() {
        when(sequenceJpaRepository.findForUpdate(companyId, "202609")).thenReturn(Optional.empty());
        when(sequenceJpaRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> sequenceService.nextNumber(companyId, "202609"))
                .isInstanceOf(IllegalStateException.class);
        verify(sequenceJpaRepository, never()).save(any());
    }
}
