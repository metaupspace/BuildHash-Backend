package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.SlotConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlotConfigurationJpaRepository extends JpaRepository<SlotConfigurationEntity, UUID> {
    List<SlotConfigurationEntity> findByIsActiveTrue();
}
