package com.builddash.backend.domain.service;

import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.CategoryAttribute;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductImage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Single implementation covering every category: validates rawAttributes generically against
 * the category's attributeSchema (type-check, enforce required) instead of hardcoding
 * per-category rules. Right amount of abstraction for Phase 1 — a factory-per-category up
 * front would be speculative given no category currently needs bespoke logic.
 */
@Component
public class SchemaDrivenProductFactory implements ProductFactory {

    @Override
    public Product build(Category category, String name, String brand, String hsnCode,
                          Map<String, Object> rawAttributes, List<ProductImage> images) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slugify(name));
        product.setCategoryId(category.getId());
        product.setBrand(brand);
        product.setHsnCode(hsnCode);
        product.setAttributes(validateAttributes(category, rawAttributes));
        product.setImages(images == null ? List.of() : images);
        product.setStatus(ProductStatus.ACTIVE);
        return product;
    }

    private Map<String, Object> validateAttributes(Category category, Map<String, Object> raw) {
        Map<String, Object> result = new HashMap<>();
        for (CategoryAttribute schema : category.getAttributeSchema()) {
            Object value = raw == null ? null : raw.get(schema.key());
            if (value == null) {
                if (schema.required()) {
                    throw new BadRequestException("INVALID_PRODUCT_ATTRIBUTES",
                            "Missing required attribute '" + schema.key() + "' for category " + category.getName());
                }
                continue;
            }
            validateType(schema, value);
            result.put(schema.key(), value);
        }
        return result;
    }

    private void validateType(CategoryAttribute schema, Object value) {
        boolean valid = switch (schema.type()) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ENUM -> value instanceof String s && schema.enumValues() != null && schema.enumValues().contains(s);
        };
        if (!valid) {
            throw new BadRequestException("INVALID_PRODUCT_ATTRIBUTES",
                    "Attribute '" + schema.key() + "' must be of type " + schema.type());
        }
    }

    private String slugify(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return base.isBlank() ? UUID.randomUUID().toString() : base;
    }
}
