package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ProductBasePriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductBasePriceJpaRepository extends JpaRepository<ProductBasePriceEntity, UUID> {
}
