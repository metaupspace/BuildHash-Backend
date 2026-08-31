package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.RfqRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RfqRouteJpaRepository extends JpaRepository<RfqRouteEntity, RfqRouteEntity.RfqRouteId> {

    List<RfqRouteEntity> findByRfqId(UUID rfqId);

    boolean existsByRfqIdAndVendorId(UUID rfqId, UUID vendorId);
}
