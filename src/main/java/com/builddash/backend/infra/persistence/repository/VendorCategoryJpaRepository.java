package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.VendorCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorCategoryJpaRepository extends JpaRepository<VendorCategoryEntity, VendorCategoryEntity.VendorCategoryId> {

    List<VendorCategoryEntity> findByVendorId(UUID vendorId);

    void deleteByVendorId(UUID vendorId);
}
