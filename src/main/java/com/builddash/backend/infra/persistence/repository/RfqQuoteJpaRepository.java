package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.RfqQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RfqQuoteJpaRepository extends JpaRepository<RfqQuoteEntity, UUID> {

    Optional<RfqQuoteEntity> findByRfqIdAndVendorId(UUID rfqId, UUID vendorId);

    List<RfqQuoteEntity> findByRfqIdOrderByTotalAmountAsc(UUID rfqId);
}
