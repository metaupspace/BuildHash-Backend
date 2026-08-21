package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.MarginRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarginRuleJpaRepository extends JpaRepository<MarginRuleEntity, UUID> {

    Optional<MarginRuleEntity> findByProductId(UUID productId);

    Optional<MarginRuleEntity> findByCategoryId(UUID categoryId);
}
