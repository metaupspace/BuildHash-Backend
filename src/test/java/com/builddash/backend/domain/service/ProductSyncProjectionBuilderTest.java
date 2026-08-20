package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.model.StockEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSyncProjectionBuilderTest {

    private final ProductSyncProjectionBuilder builder = new ProductSyncProjectionBuilder();

    private Category category() {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Cement");
        category.setSlug("cement");
        return category;
    }

    private Product product(List<StockEntry> stock) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("UltraTech Cement");
        product.setSlug("ultratech-cement");
        product.setBrand("UltraTech");
        product.setAttributes(Map.of("weightKg", 50));
        product.setStock(stock);
        product.setUpdatedAt(Instant.ofEpochMilli(1_700_000_000_000L));
        return product;
    }

    @Test
    void build_withStock_derivesInStock() {
        ProductSyncPayload payload = builder.build(product(List.of(new StockEntry("WH-1", 5))), category());

        assertThat(payload.stockStatus()).isEqualTo("in_stock");
        assertThat(payload.category()).isEqualTo("Cement");
        assertThat(payload.updatedAtEpochMillis()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void build_withZeroStock_derivesOutOfStock() {
        ProductSyncPayload payload = builder.build(product(List.of(new StockEntry("WH-1", 0))), category());

        assertThat(payload.stockStatus()).isEqualTo("out_of_stock");
    }

    @Test
    void build_withNoStockEntries_derivesOutOfStock() {
        ProductSyncPayload payload = builder.build(product(List.of()), category());

        assertThat(payload.stockStatus()).isEqualTo("out_of_stock");
    }
}
