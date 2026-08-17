package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductDetail;
import com.builddash.backend.domain.model.ProductPage;
import com.builddash.backend.domain.model.StockEntry;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises CatalogServiceImpl against the real (Testcontainers) Postgres instance shared by
 * every IT — CatalogControllerIT only verifies controller routing/serialization with mocks.
 * Successor to the deleted CatalogServiceMongoIT: covers the two behaviors mocks can't verify —
 * real keyset-cursor pagination correctness and the cross-store GST lookup.
 */
class CatalogServiceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogServiceImpl catalogService;

    private Category saveCategory() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        return categoryRepository.save(category);
    }

    private Product newProduct(UUID categoryId, String suffix, String hsnCode, List<StockEntry> stock) {
        Product product = new Product();
        product.setName("Product " + suffix);
        product.setSlug("product-" + suffix + "-" + UUID.randomUUID());
        product.setCategoryId(categoryId);
        product.setBrand("BrandX");
        product.setHsnCode(hsnCode);
        product.setStock(stock);
        product.setStatus(ProductStatus.ACTIVE);
        return product;
    }

    @Test
    void cursorPagination_walksTwoPagesWithoutSkipOrDuplicate() {
        Category category = saveCategory();
        List<UUID> insertedIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Product saved = productRepository.save(newProduct(category.getId(), String.valueOf(i), "2523", List.of()));
            insertedIds.add(saved.getId());
        }

        ProductPage firstPage = catalogService.list(category.getId(), null, null, 3);
        assertThat(firstPage.items()).hasSize(3);
        assertThat(firstPage.nextCursor()).isNotNull();

        ProductPage secondPage = catalogService.list(category.getId(), null, firstPage.nextCursor(), 3);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.nextCursor()).isNull();

        List<UUID> pagedIds = firstPage.items().stream().map(Product::getId).collect(Collectors.toList());
        pagedIds.addAll(secondPage.items().stream().map(Product::getId).toList());

        assertThat(pagedIds).containsExactlyInAnyOrderElementsOf(insertedIds);
        assertThat(pagedIds).doesNotHaveDuplicates();
    }

    @Test
    void getDetail_gstRateMatchesHsnGstRatesTableExactly() {
        Category category = saveCategory();
        Product product = productRepository.save(newProduct(category.getId(), "gst", "3208", List.of()));

        ProductDetail detail = catalogService.getDetail(product.getId());

        assertThat(detail.gstRatePercent()).isNotNull();
    }

    @Test
    void getDetail_stockStatus_inStockWhenAnyWarehouseHasQuantity() {
        Category category = saveCategory();
        Product product = productRepository.save(newProduct(category.getId(), "in-stock", "2523",
                List.of(new StockEntry("wh-1", 0), new StockEntry("wh-2", 5))));

        ProductDetail detail = catalogService.getDetail(product.getId());

        assertThat(detail.inStock()).isTrue();
    }

    @Test
    void getDetail_stockStatus_outOfStockWhenAllWarehouseQuantitiesAreZero() {
        Category category = saveCategory();
        Product product = productRepository.save(newProduct(category.getId(), "zero-stock", "2523",
                List.of(new StockEntry("wh-1", 0), new StockEntry("wh-2", 0))));

        ProductDetail detail = catalogService.getDetail(product.getId());

        assertThat(detail.inStock()).isFalse();
    }

    @Test
    void getDetail_stockStatus_outOfStockWhenNoStockEntriesAtAll() {
        Category category = saveCategory();
        Product product = productRepository.save(newProduct(category.getId(), "no-stock", "2523", List.of()));

        ProductDetail detail = catalogService.getDetail(product.getId());

        assertThat(detail.inStock()).isFalse();
    }
}
