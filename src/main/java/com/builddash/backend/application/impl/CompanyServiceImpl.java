package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;

    @Override
    @Transactional
    public Company create(UUID creatorUserId, String name, String gstNumber, String statementEmail,
                          String businessTimezone) {
        Company company = companyRepository.save(new Company(
                UUID.randomUUID(), name, gstNumber, statementEmail,
                businessTimezone == null || businessTimezone.isBlank() ? "Asia/Kolkata" : businessTimezone,
                CompanyStatus.ACTIVE, null, null));
        // Creator becomes OWNER (9-A rule) — same transaction, so a company can never
        // exist without its OWNER membership.
        companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), creatorUserId, CompanyRole.OWNER, null, null));
        return company;
    }

    @Override
    @Transactional(readOnly = true)
    public Company get(UUID companyId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
    }

    @Override
    @Transactional
    public Company update(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                          String name, String gstNumber, String statementEmail, String businessTimezone) {
        requireAdmin(companyId, actorUserId, callerMemberships);
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
    public Company updateStatus(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                                CompanyStatus status) {
        requireAdmin(companyId, actorUserId, callerMemberships);
        Company current = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        Company next = status == CompanyStatus.SUSPENDED ? current.suspend() : current.activate();
        return companyRepository.save(next);
    }

    /** Ordinary membership check from the token claim; non-members get 404 (existence hiding). */
    private static void requireMember(UUID companyId, List<B2bMembership> callerMemberships) {
        boolean member = callerMemberships.stream()
                .anyMatch(m -> m.companyId().equals(companyId));
        if (!member) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId);
        }
    }

    /**
     * Company mutations are critical B2B operations (decision 4): the role is re-checked
     * against the CURRENT membership row in the database, so a revoked admin acts on a
     * stale token in vain. Reads above use the claim only.
     */
    private CompanyMember requireAdmin(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        CompanyMember db = companyMemberRepository.findByCompanyIdAndUserId(companyId, actorUserId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        if (!db.role().atLeast(CompanyRole.ADMIN)) {
            throw new ForbiddenException("FORBIDDEN", "Company admin role required");
        }
        return db;
    }
}
