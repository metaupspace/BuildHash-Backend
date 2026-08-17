package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductPageCursor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Optional<Product> findById(UUID id);

    /**
     * Keyset page: strictly after cursor (createdAt, id) in ascending order, or the first
     * page when cursor is null. Requests limit+1 rows so the caller can detect "has next".
     */
    List<Product> findPage(UUID categoryId, String brand, ProductPageCursor cursor, int limit);

    Product save(Product product);
}
