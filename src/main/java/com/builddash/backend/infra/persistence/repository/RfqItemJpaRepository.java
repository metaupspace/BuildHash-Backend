package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.RfqItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RfqItemJpaRepository extends JpaRepository<RfqItemEntity, UUID> {

    List<RfqItemEntity> findByRfqId(UUID rfqId);
}
