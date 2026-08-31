package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CompanySiteService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.exception.SiteNameTakenException;
import com.builddash.backend.domain.model.CompanySite;
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

    private final B2bAuthorizer authorizer;
    private final CompanySiteRepository companySiteRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CompanySite create(UUID companyId, UUID actorUserId, String name, UUID addressId) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.SITE_MANAGE, null, true);
        try {
            return companySiteRepository.save(new CompanySite(
                    UUID.randomUUID(), companyId, name, addressId, true, null, null));
        } catch (DataIntegrityViolationException e) {
            throw new SiteNameTakenException(name);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanySite> listSites(UUID companyId, UUID userId) {
        authorizer.authorize(userId, companyId, CompanyPermission.SITE_VIEW, null, false);
        return companySiteRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional
    public CompanySite update(UUID companyId, UUID siteId, UUID actorUserId,
                              String name, UUID addressId, Boolean active) {
        authorizer.authorize(actorUserId, companyId, CompanyPermission.SITE_MANAGE, siteId, true);

        // Lock the site row FIRST — the shared serialization point with the future
        // 9-B/9-C order-association flows (CompanySiteJpaRepository#findByIdForUpdate).
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
}
