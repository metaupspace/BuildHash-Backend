package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.model.StockEntry;
import org.springframework.stereotype.Component;

@Component
public class ProductSyncProjectionBuilder {

    public ProductSyncPayload build(Product product, Category category) {
        boolean inStock = product.getStock().stream().mapToInt(StockEntry::quantity).sum() > 0;
        return new ProductSyncPayload(
                product.getId(),
                product.getName(),
                product.getSlug(),
                category.getName(),
                product.getBrand(),
                product.getAttributes(),
                inStock ? "in_stock" : "out_of_stock",
                product.getUpdatedAt() == null ? 0L : product.getUpdatedAt().toEpochMilli()
        );
    }
}
