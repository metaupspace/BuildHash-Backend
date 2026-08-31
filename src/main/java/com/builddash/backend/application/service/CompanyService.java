package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.Company;

import java.util.List;
import java.util.UUID;

public interface CompanyService {

    /** Creates the company and the creator's OWNER membership in one transaction. */
    Company create(UUID creatorUserId, String name, String gstNumber, String statementEmail, String businessTimezone);

    /** Member-only read (any company role); non-members get COMPANY_NOT_FOUND (404 convention). */
    Company get(UUID companyId, List<B2bMembership> callerMemberships);

    /** ADMIN+ (rank check). Updates identity/contact fields. */
    Company update(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                   String name, String gstNumber, String statementEmail, String businessTimezone);

    /** ADMIN+; status transition ACTIVE <-> SUSPENDED. */
    Company updateStatus(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                         CompanyStatus status);
}
