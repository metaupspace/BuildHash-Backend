package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CategoryReader;
import com.builddash.backend.application.service.ProductReader;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductDetail;
import com.builddash.backend.domain.model.ProductPage;
import com.builddash.backend.domain.model.ProductPageCursor;
import com.builddash.backend.domain.model.StockEntry;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class CatalogServiceImpl implements CategoryReader, ProductReader {

    private static final int MAX_LIMIT = 100;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final HsnGstRateRepository hsnGstRateRepository;



    @Override
    public List<Category> listAll() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + id));
    }

    @Override
    public ProductPage list(UUID categoryId, String brand, ProductPageCursor cursor, int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<Product> page = productRepository.findPage(categoryId, brand, cursor, effectiveLimit + 1);

        boolean hasNext = page.size() > effectiveLimit;
        List<Product> pageItems = hasNext ? page.subList(0, effectiveLimit) : page;
        ProductPageCursor nextCursor = hasNext
                ? cursorFor(pageItems.get(pageItems.size() - 1))
                : null;

        return new ProductPage(pageItems, nextCursor);
    }

    @Override
    public ProductDetail getDetail(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + id));
        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        BigDecimal gstRatePercent = hsnGstRateRepository.findByHsnCode(product.getHsnCode())
                .map(HsnGstRate::getGstRatePercent)
                .orElse(null);
        boolean inStock = product.getStock().stream().mapToInt(StockEntry::quantity).sum() > 0;

        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getCategoryId(),
                category == null ? null : category.getName(),
                product.getBrand(),
                product.getHsnCode(),
                gstRatePercent,
                product.getAttributes(),
                product.getImages(),
                inStock,
                product.getStatus()
        );
    }

    private ProductPageCursor cursorFor(Product product) {
        return new ProductPageCursor(product.getCreatedAt(), product.getId());
    }
}
