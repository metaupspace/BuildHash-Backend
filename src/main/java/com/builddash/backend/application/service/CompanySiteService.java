package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanySite;

import java.util.List;
import java.util.UUID;

public interface CompanySiteService {

    /** ADMIN+; unique name per company enforced by uq_company_sites_name (409 on duplicate). */
    CompanySite create(UUID companyId, UUID actorUserId, List<B2bMembership> callerMemberships,
                       String name, UUID addressId);

    /** Member-only read. */
    List<CompanySite> listSites(UUID companyId, List<B2bMembership> callerMemberships);

    /**
     * ADMIN+; DB re-checked. Deactivation runs under the site row lock and rejects with
     * SiteInUseException (409) while non-CANCELLED orders reference the site. The
     * mirrored lock contract for 9-B/9-C order association is documented on
     * CompanySiteJpaRepository#findByIdForUpdate.
     */
    CompanySite update(UUID companyId, UUID siteId, UUID actorUserId, List<B2bMembership> callerMemberships,
                       String name, UUID addressId, Boolean active);
}
