package com.builddash.backend.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface LoginEventJpaRepository extends JpaRepository<LoginEventEntity, UUID> {

    List<LoginEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
