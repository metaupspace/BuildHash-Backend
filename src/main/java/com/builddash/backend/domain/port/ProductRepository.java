package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductPageCursor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Optional<Product> findById(UUID id);

    /**
     * Business-identifier lookup for PO bulk imports (9-C): products.slug is the
     * catalog's identifier (no SKU field exists — locked 9-C decision 3). slug is
     * NOT unique at the schema level, so this returns every match — the caller
     * must treat 0 as NOT_FOUND and >1 as AMBIGUOUS, never pick the first.
     */
    List<Product> findBySlug(String slug);

    /**
     * Keyset page: strictly after cursor (createdAt, id) in ascending order, or the first
     * page when cursor is null. Requests limit+1 rows so the caller can detect "has next".
     */
    List<Product> findPage(UUID categoryId, String brand, ProductPageCursor cursor, int limit);

    Product save(Product product);
}
