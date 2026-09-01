package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.Company;

import java.util.UUID;

public interface CompanyService {

    /** Creates the company, the creator's OWNER membership, and default permission profiles in one transaction. */
    Company create(UUID creatorUserId, String name, String gstNumber, String statementEmail, String businessTimezone);

    /** COMPANY_VIEW. */
    Company get(UUID companyId, UUID userId);

    /** COMPANY_UPDATE (critical). */
    Company update(UUID companyId, UUID actorUserId, String name, String gstNumber,
                   String statementEmail, String businessTimezone);

    /** COMPANY_UPDATE (critical). */
    Company updateStatus(UUID companyId, UUID actorUserId, java.util.List<String> actorRoles, CompanyStatus status);
}
