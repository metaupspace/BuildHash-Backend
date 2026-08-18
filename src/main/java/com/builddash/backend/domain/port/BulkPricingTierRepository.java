package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.BulkPricingTier;

import java.util.List;
import java.util.UUID;

public interface BulkPricingTierRepository {

    List<BulkPricingTier> findByProductId(UUID productId);
}
