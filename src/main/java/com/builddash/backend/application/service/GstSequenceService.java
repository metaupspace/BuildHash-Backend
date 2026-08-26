package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.infra.persistence.entity.GstSequenceEntity;
import com.builddash.backend.infra.persistence.repository.GstSequenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GstSequenceService {

    private final GstSequenceJpaRepository sequenceJpaRepository;

    @Transactional
    public String nextNumber(GstSequenceType sequenceType) {
        GstSequenceEntity sequence = sequenceJpaRepository.findBySequenceTypeForUpdate(sequenceType)
                .orElseThrow(() -> new IllegalStateException("GST sequence not initialized for type: " + sequenceType));

        long nextVal = sequence.getCurrentVal() + 1;
        sequence.setCurrentVal(nextVal);
        sequence.setUpdatedAt(Instant.now());
        sequenceJpaRepository.save(sequence);

        return String.format("%s%06d", sequence.getPrefix(), nextVal);
    }
}
