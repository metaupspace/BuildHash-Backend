package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the outbox pattern's core guarantee: the Product write and its outbox row land in
 * the same commit.
 */
class CatalogWriteServiceImplJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogWriteServiceImpl catalogWriteService;

    @Autowired
    private CatalogOutboxEventRepository catalogOutboxEventRepository;

    @Test
    void saveProductAndEnqueueSync_writesProductAndPendingOutboxEventTogether() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Product");
        product.setSlug("product-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = catalogWriteService.saveProductAndEnqueueSync(product);

        List<CatalogOutboxEvent> pending = catalogOutboxEventRepository.findByStatus(OutboxStatus.PENDING);
        assertThat(pending).anySatisfy(event -> {
            assertThat(event.getProductId()).isEqualTo(saved.getId());
            assertThat(event.getEventType()).isEqualTo(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
            assertThat(event.getPayload()).contains(saved.getId().toString());
        });
    }
}
