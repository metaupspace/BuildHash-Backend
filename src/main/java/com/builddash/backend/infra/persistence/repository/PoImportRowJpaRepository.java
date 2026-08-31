package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.infra.persistence.entity.PoImportRowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PoImportRowJpaRepository extends JpaRepository<PoImportRowEntity, UUID> {

    List<PoImportRowEntity> findByImportId(UUID importId);

    long countByImportIdAndStatus(UUID importId, PoRowStatus status);
}
