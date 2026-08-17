package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductPageCursor;
import com.builddash.backend.domain.port.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(ProductMapper::toDomain);
    }

    @Override
    public List<Product> findPage(UUID categoryId, String brand, ProductPageCursor cursor, int limit) {
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        UUID cursorId = cursor == null ? null : cursor.id();
        return jpaRepository.findActivePage(ProductStatus.ACTIVE, categoryId, brand, cursorCreatedAt, cursorId,
                        PageRequest.of(0, limit))
                .stream()
                .map(ProductMapper::toDomain)
                .toList();
    }

    @Override
    public Product save(Product product) {
        return ProductMapper.toDomain(jpaRepository.save(ProductMapper.toEntity(product)));
    }
}
