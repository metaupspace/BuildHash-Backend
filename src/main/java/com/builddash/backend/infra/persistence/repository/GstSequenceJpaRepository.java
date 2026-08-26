package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.GstSequenceType;
import com.builddash.backend.infra.persistence.entity.GstSequenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GstSequenceJpaRepository extends JpaRepository<GstSequenceEntity, GstSequenceType> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM GstSequenceEntity s WHERE s.sequenceType = :sequenceType")
    Optional<GstSequenceEntity> findBySequenceTypeForUpdate(@Param("sequenceType") GstSequenceType sequenceType);
}
