package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.CompanySite;

import java.util.List;
import java.util.UUID;

public interface CompanySiteService {

    /** SITE_MANAGE (critical); unique name per company -> 409. */
    CompanySite create(UUID companyId, UUID actorUserId, String name, UUID addressId);

    /** SITE_VIEW. */
    List<CompanySite> listSites(UUID companyId, UUID userId);

    /**
     * SITE_MANAGE (critical). Deactivation runs under the site row lock and rejects
     * with SiteInUseException (409) while non-CANCELLED orders reference the site.
     */
    CompanySite update(UUID companyId, UUID siteId, UUID actorUserId,
                       String name, UUID addressId, Boolean active);
}
