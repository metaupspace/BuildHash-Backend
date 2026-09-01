package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.StatementSequenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StatementSequenceJpaRepository extends JpaRepository<StatementSequenceEntity, StatementSequenceEntity.SequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StatementSequenceEntity s WHERE s.companyId = :companyId AND s.periodKey = :periodKey")
    Optional<StatementSequenceEntity> findForUpdate(@Param("companyId") UUID companyId,
                                                    @Param("periodKey") String periodKey);
}
