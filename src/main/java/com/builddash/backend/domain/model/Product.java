package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ProductStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Product {

    private UUID id;
    private String name;
    private String slug;
    private UUID categoryId;
    private String brand;

    /** Matches Postgres hsn_gst_rates.hsn_code — not a DB-level FK (cross-table reference). */
    private String hsnCode;

    private Map<String, Object> attributes = new HashMap<>();
    private List<ProductImage> images = new ArrayList<>();
    private List<StockEntry> stock = new ArrayList<>();
    private ProductStatus status = ProductStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;
}
