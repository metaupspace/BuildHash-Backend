package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.infra.persistence.entity.GstSequenceEntity;
import com.builddash.backend.infra.persistence.repository.GstSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GstSequenceService {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final GstSequenceJpaRepository sequenceJpaRepository;

    @Transactional
    public String nextNumber(GstSequenceType sequenceType) {
        return nextNumber(sequenceType, Instant.now());
    }

    @Transactional
    public String nextNumber(GstSequenceType sequenceType, Instant asOf) {
        ZonedDateTime zdt = (asOf != null ? asOf : Instant.now()).atZone(IST_ZONE);
        int year = zdt.getYear();
        int month = zdt.getMonthValue();
        int startYear = month >= 4 ? year : year - 1;
        int endYear = startYear + 1;

        String fiscalYear = String.format("%04d-%04d", startYear, endYear);
        String shortFy = String.format("%02d%02d", startYear % 100, endYear % 100);

        String prefix = switch (sequenceType) {
            case INVOICE -> String.format("INV-%s-", shortFy);
            case CREDIT_NOTE -> String.format("CRN-%s-", shortFy);
            case DEBIT_NOTE -> String.format("DBN-%s-", shortFy);
        };

        Optional<GstSequenceEntity> sequenceOpt = sequenceJpaRepository
                .findBySequenceTypeAndFiscalYearForUpdate(sequenceType, fiscalYear);

        long nextVal;
        if (sequenceOpt.isPresent()) {
            GstSequenceEntity sequence = sequenceOpt.get();
            nextVal = sequence.getCurrentVal() + 1;
            sequence.setCurrentVal(nextVal);
            sequence.setUpdatedAt(Instant.now());
            sequenceJpaRepository.save(sequence);
            return String.format("%s%06d", sequence.getPrefix(), nextVal);
        } else {
            nextVal = 1L;
            GstSequenceEntity newSequence = new GstSequenceEntity(
                    sequenceType,
                    fiscalYear,
                    prefix,
                    nextVal,
                    Instant.now()
            );
            sequenceJpaRepository.save(newSequence);
            return String.format("%s%06d", prefix, nextVal);
        }
    }
}
