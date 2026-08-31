package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanyContractPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyContractPriceJpaRepository extends JpaRepository<CompanyContractPriceEntity, UUID> {

    List<CompanyContractPriceEntity> findByCompanyIdAndProductId(UUID companyId, UUID productId);
}
