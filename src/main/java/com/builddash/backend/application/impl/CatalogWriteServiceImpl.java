package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CatalogWriteService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.service.ProductSyncProjectionBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogWriteServiceImpl implements CatalogWriteService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogOutboxEventRepository catalogOutboxEventRepository;
    private final ProductSyncProjectionBuilder productSyncProjectionBuilder;
    private final ObjectMapper objectMapper;

    public CatalogWriteServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository,
                                    CatalogOutboxEventRepository catalogOutboxEventRepository,
                                    ProductSyncProjectionBuilder productSyncProjectionBuilder,
                                    ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.catalogOutboxEventRepository = catalogOutboxEventRepository;
        this.productSyncProjectionBuilder = productSyncProjectionBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Product saveProductAndEnqueueSync(Product product) {
        Product saved = productRepository.save(product);

        Category category = categoryRepository.findById(saved.getCategoryId())
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND",
                        "Category not found: " + saved.getCategoryId()));
        ProductSyncPayload payload = productSyncProjectionBuilder.build(saved, category);

        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setProductId(saved.getId());
        event.setEventType(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
        event.setPayload(writePayload(payload));
        catalogOutboxEventRepository.save(event);

        return saved;
    }

    private String writePayload(ProductSyncPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize product sync payload", e);
        }
    }
}
