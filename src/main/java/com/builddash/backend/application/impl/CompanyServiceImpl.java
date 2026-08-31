package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.service.CompanyPermissionDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final B2bAuthorizer authorizer;

    @Override
    @Transactional
    public Company create(UUID creatorUserId, String name, String gstNumber, String statementEmail,
                          String businessTimezone) {
        Company company = companyRepository.save(new Company(
                UUID.randomUUID(), name, gstNumber, statementEmail,
                businessTimezone == null || businessTimezone.isBlank() ? "Asia/Kolkata" : businessTimezone,
                CompanyStatus.ACTIVE, null, null));
        // Creator becomes OWNER, and every non-OWNER role receives its default
        // profile — same transaction, so a company can never exist half-initialized
        // (no members without permissions, no permissions without the company).
        companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), creatorUserId, CompanyRole.OWNER, null, null));
        for (CompanyRole role : CompanyPermissionDefaults.customizableRoles()) {
            companyRolePermissionRepository.replaceRolePermissions(
                    company.id(), role, CompanyPermissionDefaults.forRole(role));
        }
        return company;
    }

    @Override
    @Transactional(readOnly = true)
    public Company get(UUID companyId, UUID userId) {
        authorizer.authorize(userId, companyId, CompanyPermission.COMPANY_VIEW, null, false);
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
    }

    @Override
    @Transactional
    public Company update(UUID companyId, UUID actorUserId, String name, String gstNumber,
                          String statementEmail, String businessTimezone) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
        Company current = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        return companyRepository.save(new Company(
                current.id(),
                name != null ? name : current.name(),
                gstNumber != null ? gstNumber : current.gstNumber(),
                statementEmail != null ? statementEmail : current.statementEmail(),
                businessTimezone != null ? businessTimezone : current.businessTimezone(),
                current.status(), current.createdAt(), current.updatedAt()));
    }

    @Override
    @Transactional
    public Company updateStatus(UUID companyId, UUID actorUserId, CompanyStatus status) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
        Company current = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        Company next = status == CompanyStatus.SUSPENDED ? current.suspend() : current.activate();
        return companyRepository.save(next);
    }
}
