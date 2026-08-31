package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ContractPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractPriceJpaRepository extends JpaRepository<ContractPriceEntity, UUID> {

    List<ContractPriceEntity> findByUserIdAndProductId(UUID userId, UUID productId);

    java.util.List<ContractPriceEntity> findByUserId(UUID userId);
}
