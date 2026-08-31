package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Vendor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository {

    Vendor save(Vendor vendor);

    Optional<Vendor> findById(UUID id);

    List<Vendor> findAll();

    /**
     * Routing query: distinct ACTIVE-category matches — every vendor whose
     * category mapping intersects ANY category represented by the given
     * products. Used once at RFQ creation to snapshot rfq_routes.
     */
    List<Vendor> findRoutableVendors(Collection<UUID> productIds);
}
