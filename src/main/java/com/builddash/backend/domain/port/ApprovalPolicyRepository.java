package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ApprovalPolicy;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalPolicyRepository {

    Optional<ApprovalPolicy> findByCompanyId(UUID companyId);

    ApprovalPolicy save(ApprovalPolicy policy);
}
