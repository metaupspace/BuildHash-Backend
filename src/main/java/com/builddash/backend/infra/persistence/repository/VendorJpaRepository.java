package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.VendorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VendorJpaRepository extends JpaRepository<VendorEntity, UUID> {

    /**
     * Routing query (9-B): vendors matching ANY category represented by the given
     * products — vendor_categories ∩ products.category_id. DISTINCT collapses a
     * vendor matching several of the RFQ's categories into one route row.
     */
    @Query("""
            SELECT DISTINCT v FROM VendorEntity v
            WHERE v.id IN (
                SELECT vc.vendorId FROM VendorCategoryEntity vc
                WHERE vc.categoryId IN (
                    SELECT p.categoryId FROM ProductEntity p WHERE p.id IN :productIds
                )
            )
            """)
    List<VendorEntity> findRoutableVendors(@Param("productIds") Collection<UUID> productIds);
}
