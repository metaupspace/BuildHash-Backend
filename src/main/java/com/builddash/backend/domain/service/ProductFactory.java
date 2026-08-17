package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductImage;

import java.util.List;
import java.util.Map;

/**
 * Assembles a valid {@link Product} for a given category. One interface instead of a
 * category-conditional god-method: today one schema-driven implementation validates every
 * category generically against its attributeSchema; a category that later needs bespoke
 * construction logic gets its own implementation behind this same interface (OCP).
 *
 * <p>Takes the resolved {@link Category} directly — the lookup is the caller's job
 * (application/impl), keeping this pure domain logic with no repository dependency.
 */
public interface ProductFactory {

    Product build(Category category,
                  String name,
                  String brand,
                  String hsnCode,
                  Map<String, Object> rawAttributes,
                  List<ProductImage> images);
}
