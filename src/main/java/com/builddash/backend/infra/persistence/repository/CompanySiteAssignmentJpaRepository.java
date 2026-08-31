package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanySiteAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanySiteAssignmentJpaRepository
        extends JpaRepository<CompanySiteAssignmentEntity, CompanySiteAssignmentEntity.AssignmentId> {

    List<CompanySiteAssignmentEntity> findByMemberId(UUID memberId);

    boolean existsByMemberIdAndSiteId(UUID memberId, UUID siteId);

    List<CompanySiteAssignmentEntity> findBySiteId(UUID siteId);

    void deleteByMemberId(UUID memberId);
}
