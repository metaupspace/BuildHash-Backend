package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CompanySiteService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.exception.SiteNameTakenException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanySiteServiceImpl implements CompanySiteService {

    private final CompanySiteRepository companySiteRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CompanySite create(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                              String name, UUID addressId) {
        requireAdmin(companyId, actorUserId, callerMemberships);
        try {
            return companySiteRepository.save(new CompanySite(
                    UUID.randomUUID(), companyId, name, addressId, true, null, null));
        } catch (DataIntegrityViolationException e) {
            throw new SiteNameTakenException(name);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanySite> listSites(UUID companyId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        return companySiteRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional
    public CompanySite update(UUID companyId, UUID siteId, UUID actorUserId,
                              List<B2bMembership> callerMemberships,
                              String name, UUID addressId, Boolean active) {
        requireAdmin(companyId, actorUserId, callerMemberships);

        // Lock the site row FIRST — the shared serialization point with the future
        // 9-B/9-C order-association flows (see CompanySiteJpaRepository#findByIdForUpdate).
        CompanySite site = companySiteRepository.findByIdForUpdate(siteId);
        if (!site.companyId().equals(companyId)) {
            // Cross-company attempt: same 404 as a missing site, no existence leak
            throw new NotFoundException("COMPANY_SITE_NOT_FOUND", "Company site not found: " + siteId);
        }

        if (Boolean.FALSE.equals(active) && site.active()) {
            long activeOrders = orderRepository.countActiveOrdersForSite(siteId);
            if (activeOrders > 0) {
                throw new SiteInUseException(siteId, activeOrders);
            }
        }

        try {
            return companySiteRepository.save(new CompanySite(
                    site.id(), site.companyId(),
                    name != null ? name : site.name(),
                    addressId != null ? addressId : site.addressId(),
                    active != null ? active : site.active(),
                    site.createdAt(), site.updatedAt()));
        } catch (DataIntegrityViolationException e) {
            throw new SiteNameTakenException(name);
        }
    }

    private static void requireMember(UUID companyId, List<B2bMembership> callerMemberships) {
        boolean member = callerMemberships.stream()
                .anyMatch(m -> m.companyId().equals(companyId));
        if (!member) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId);
        }
    }

    /** Critical B2B mutation: role re-checked against the current DB membership row. */
    private void requireAdmin(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships) {
        requireMember(companyId, callerMemberships);
        var db = companyMemberRepository.findByCompanyIdAndUserId(companyId, actorUserId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found: " + companyId));
        if (!db.role().atLeast(CompanyRole.ADMIN)) {
            throw new ForbiddenException("FORBIDDEN", "Company admin role required");
        }
    }
}
