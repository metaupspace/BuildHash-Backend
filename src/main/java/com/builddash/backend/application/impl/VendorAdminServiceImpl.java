package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.VendorAdminService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.RfqValidationException;
import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorAdminServiceImpl implements VendorAdminService {

    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public Vendor create(String name, List<UUID> categoryIds) {
        validateName(name);
        List<UUID> categories = normalizeCategories(categoryIds);
        Vendor vendor = new Vendor(UUID.randomUUID(), name, true, categories, null, null);
        return vendorRepository.save(vendor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vendor> list() {
        return vendorRepository.findAll();
    }

    /**
     * Name/category/active updates only. Historical RFQ routing and quotes are
     * never recalculated or destroyed: rfq_routes and rfq_quotes reference the
     * vendor row, which a PATCH never replaces.
     */
    @Override
    @Transactional
    public Vendor update(UUID vendorId, String name, List<UUID> categoryIds, Boolean active) {
        Vendor existing = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new NotFoundException("VENDOR_NOT_FOUND", "Vendor not found: " + vendorId));
        if (name != null) {
            validateName(name);
        }
        List<UUID> categories = categoryIds != null ? normalizeCategories(categoryIds) : existing.categoryIds();
        return vendorRepository.save(existing.with(name, categories, active));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new RfqValidationException("VENDOR_NAME_REQUIRED", "Vendor name is required");
        }
    }

    /** At least one category is required for vendor administration. */
    private List<UUID> normalizeCategories(List<UUID> categoryIds) {
        List<UUID> distinct = categoryIds == null ? List.of() : categoryIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            throw new RfqValidationException("VENDOR_CATEGORY_REQUIRED",
                    "At least one category is required for vendor administration");
        }
        for (UUID categoryId : distinct) {
            if (categoryRepository.findById(categoryId).isEmpty()) {
                throw new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + categoryId);
            }
        }
        return distinct;
    }
}
