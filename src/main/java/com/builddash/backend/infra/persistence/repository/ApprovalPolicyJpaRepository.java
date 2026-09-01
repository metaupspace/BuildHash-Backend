package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ApprovalPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalPolicyJpaRepository extends JpaRepository<ApprovalPolicyEntity, UUID> {

    Optional<ApprovalPolicyEntity> findByCompanyId(UUID companyId);
}
