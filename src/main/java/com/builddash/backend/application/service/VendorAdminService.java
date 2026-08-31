package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Vendor;

import java.util.List;
import java.util.UUID;

/**
 * Application-ADMIN vendor management (SecurityConfig enforces ROLE_ADMIN on
 * /admin/**). No vendor authentication, no vendor portal in 9-B.
 */
public interface VendorAdminService {

    Vendor create(String name, List<UUID> categoryIds);

    List<Vendor> list();

    /** Partial update: null fields leave the stored value unchanged. */
    Vendor update(UUID vendorId, String name, List<UUID> categoryIds, Boolean active);
}
