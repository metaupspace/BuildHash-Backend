package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Product;

public interface CatalogWriteService {

    /**
     * Saves the product and enqueues its outbox sync event in the same transaction —
     * PLAN_PHASE1.md Section 3's "domain write + outbox row = one commit".
     */
    Product saveProductAndEnqueueSync(Product product);
}
