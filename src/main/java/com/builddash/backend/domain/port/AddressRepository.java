package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository {
    Address save(Address address);
    Optional<Address> findById(UUID id);
    List<Address> findByUserId(UUID userId);
    void deleteById(UUID id);

    /**
     * DPDP (PLAN_PHASE8 5(d), FK-resolved): orders.address_id is a NOT NULL FK, so a
     * retained order pins its address row. Addresses referenced by any order are ANONYMIZED
     * (line1/line2/lat/lng nulled — content gone, row kept for FK/tax integrity); addresses
     * no order references are hard-deleted. Confirmed with the product owner at build time.
     */
    void anonymizeOrderReferencedByUserId(UUID userId);

    /** See {@link #anonymizeOrderReferencedByUserId} — the unreferenced half of the pair. */
    void deleteUnreferencedByUserId(UUID userId);
}
