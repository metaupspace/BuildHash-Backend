package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.ProductImage;
import com.builddash.backend.domain.model.StockEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {

    @Id
    @UuidGenerator
    private UUID id;

    private String name;
    private String slug;

    @Column(name = "category_id")
    private UUID categoryId;

    private String brand;

    @Column(name = "hsn_code")
    private String hsnCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> attributes = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<ProductImage> images = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<StockEntry> stock = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
