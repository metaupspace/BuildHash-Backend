package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.infra.persistence.entity.GstSequenceEntity;
import com.builddash.backend.infra.persistence.entity.GstSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GstSequenceJpaRepository extends JpaRepository<GstSequenceEntity, GstSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM GstSequenceEntity s WHERE s.sequenceType = :sequenceType AND s.fiscalYear = :fiscalYear")
    Optional<GstSequenceEntity> findBySequenceTypeAndFiscalYearForUpdate(
            @Param("sequenceType") GstSequenceType sequenceType,
            @Param("fiscalYear") String fiscalYear
    );
}
