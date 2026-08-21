package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.infra.persistence.entity.ProductBasePriceEntity;
import com.builddash.backend.infra.persistence.repository.ProductBasePriceJpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProductBasePriceRepositoryAdapter implements ProductBasePriceRepository {

    private final ProductBasePriceJpaRepository jpaRepository;

    ProductBasePriceRepositoryAdapter(ProductBasePriceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<BigDecimal> findByProductId(UUID productId) {
        return jpaRepository.findById(productId).map(ProductBasePriceEntity::getPrice);
    }

    @Override
    public BigDecimal save(UUID productId, BigDecimal price) {
        ProductBasePriceEntity entity = new ProductBasePriceEntity();
        entity.setProductId(productId);
        entity.setPrice(price);
        return jpaRepository.save(entity).getPrice();
    }
}
