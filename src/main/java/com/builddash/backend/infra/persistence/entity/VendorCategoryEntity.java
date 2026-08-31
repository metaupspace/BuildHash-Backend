package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Vendor-to-catalog-category mapping; composite PK (vendor_id, category_id). */
@Entity
@Table(name = "vendor_categories")
@IdClass(VendorCategoryEntity.VendorCategoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VendorCategoryEntity {

    @Id
    @Column(name = "vendor_id")
    private UUID vendorId;

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class VendorCategoryId implements Serializable {
        private UUID vendorId;
        private UUID categoryId;
    }
}
