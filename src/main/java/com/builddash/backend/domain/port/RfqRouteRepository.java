package com.builddash.backend.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** rfq_routes persistence: the creation-time routing snapshot. */
public interface RfqRouteRepository {

    void saveAll(UUID rfqId, Collection<UUID> vendorIds);

    List<UUID> findVendorIdsByRfqId(UUID rfqId);

    boolean existsByRfqIdAndVendorId(UUID rfqId, UUID vendorId);
}
