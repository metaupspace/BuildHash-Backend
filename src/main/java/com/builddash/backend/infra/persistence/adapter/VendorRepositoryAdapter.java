package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.port.VendorRepository;
import com.builddash.backend.infra.persistence.entity.VendorCategoryEntity;
import com.builddash.backend.infra.persistence.entity.VendorEntity;
import com.builddash.backend.infra.persistence.repository.VendorCategoryJpaRepository;
import com.builddash.backend.infra.persistence.repository.VendorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class VendorRepositoryAdapter implements VendorRepository {

    private final VendorJpaRepository jpaRepository;
    private final VendorCategoryJpaRepository categoryJpaRepository;

    @Override
    @Transactional
    public Vendor save(Vendor vendor) {
        VendorEntity entity = jpaRepository.findById(vendor.id())
                .orElseGet(() -> {
                    VendorEntity e = new VendorEntity();
                    e.setId(vendor.id());
                    return e;
                });
        entity.setName(vendor.name());
        entity.setActive(vendor.active());
        VendorEntity saved = jpaRepository.saveAndFlush(entity);

        // Category mapping is replace-on-write: vendor_categories has no history.
        categoryJpaRepository.deleteByVendorId(saved.getId());
        categoryJpaRepository.saveAll(vendor.categoryIds().stream()
                .map(categoryId -> new VendorCategoryEntity(saved.getId(), categoryId))
                .toList());
        categoryJpaRepository.flush();

        return toDomain(saved, vendor.categoryIds());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vendor> findById(UUID id) {
        return jpaRepository.findById(id).map(entity ->
                toDomain(entity, categoryJpaRepository.findByVendorId(id).stream()
                        .map(VendorCategoryEntity::getCategoryId).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vendor> findAll() {
        List<VendorEntity> vendors = jpaRepository.findAll();
        return vendors.stream()
                .map(entity -> toDomain(entity, categoryJpaRepository.findByVendorId(entity.getId()).stream()
                        .map(VendorCategoryEntity::getCategoryId).toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vendor> findRoutableVendors(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findRoutableVendors(productIds).stream()
                .map(entity -> toDomain(entity, List.of()))
                .toList();
    }

    private Vendor toDomain(VendorEntity entity, List<UUID> categoryIds) {
        return new Vendor(entity.getId(), entity.getName(), entity.isActive(),
                categoryIds, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
