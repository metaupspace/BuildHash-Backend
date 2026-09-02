package com.builddash.backend.infra.search;

import com.builddash.backend.application.scheduler.CatalogReindexer;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.service.ProductSyncProjectionBuilder;
import com.builddash.backend.support.RecordingSearchIndexAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchBulkReindexJpaIT {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductSyncProjectionBuilder productSyncProjectionBuilder;

    @Mock
    private CatalogOutboxEventRepository catalogOutboxEventRepository;

    private RecordingSearchIndexAdmin searchIndexAdmin;
    private CatalogReindexer reindexer;

    @BeforeEach
    void setUp() {
        searchIndexAdmin = new RecordingSearchIndexAdmin();
        reindexer = new CatalogReindexer(
                productRepository,
                categoryRepository,
                productSyncProjectionBuilder,
                searchIndexAdmin,
                catalogOutboxEventRepository
        );
    }

    @Test
    void reindex_indexesInBulk_populatesNewIndexAndSwapsAlias() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Materials");
        category.setSlug("materials");
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            UUID prodId = UUID.randomUUID();
            Product p = new Product();
            p.setId(prodId);
            p.setName("Cement Bag " + i);
            p.setSlug("cement-" + i);
            p.setCategoryId(categoryId);
            p.setStatus(ProductStatus.ACTIVE);
            p.setHsnCode("2523");
            p.setCreatedAt(Instant.now().plusMillis(i));
            p.setUpdatedAt(Instant.now().plusMillis(i));
            products.add(p);

            ProductSyncPayload payload = new ProductSyncPayload(
                    prodId,
                    p.getName(),
                    p.getSlug(),
                    "materials",
                    "BrandX",
                    Map.of(),
                    "IN_STOCK",
                    p.getUpdatedAt().toEpochMilli()
            );
            when(productSyncProjectionBuilder.build(eq(p), eq(category))).thenReturn(payload);
        }

        // Page 1: 201 items returned (indicates hasNext = true, items 0..200 processed in bulk 1)
        org.mockito.Mockito.doReturn(products.subList(0, 201))
                .when(productRepository).findPage(eq(null), eq(null), org.mockito.ArgumentMatchers.isNull(), eq(201));

        // Page 2: remaining 50 items returned (hasNext = false, items 200..250 processed in bulk 2)
        org.mockito.Mockito.doReturn(products.subList(200, 250))
                .when(productRepository).findPage(eq(null), eq(null), org.mockito.ArgumentMatchers.notNull(), eq(201));

        reindexer.reindex();

        String activeIndex = searchIndexAdmin.resolveAlias("products");
        assertThat(activeIndex).isNotNull();
        assertThat(searchIndexAdmin.documentCount(activeIndex)).isEqualTo(250);
    }
}
